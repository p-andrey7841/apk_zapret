package com.zapret.droid.proxy

import android.net.VpnService
import com.zapret.droid.strategies.Strategy
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class UdpProxyHandler(
    private val vpnService: VpnService,
    private val strategy: Strategy,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class UdpSession(
        val key: ConnectionKey,
        val socket: DatagramSocket,
        var lastActivity: Long = System.currentTimeMillis(),
        val job: Job? = null
    )

    private val sessions = ConcurrentHashMap<ConnectionKey, UdpSession>()
    private val SESSION_TIMEOUT_MS = 30_000L

    fun handlePacket(buf: ByteArray, writeTun: (ByteArray) -> Unit) {
        val ipHL = PacketUtils.parseIpHeaderLen(buf)
        val srcIp = PacketUtils.parseIpSrc(buf)
        val dstIp = PacketUtils.parseIpDst(buf)
        val srcPort = PacketUtils.parseUdpSrcPort(buf, ipHL)
        val dstPort = PacketUtils.parseUdpDstPort(buf, ipHL)
        val payload = PacketUtils.parseUdpPayload(buf, ipHL)

        val key = ConnectionKey(
            PacketUtils.bytesToIp(srcIp), srcPort,
            PacketUtils.bytesToIp(dstIp), dstPort,
            17
        )

        val session = sessions.getOrPut(key) {
            createSession(key, srcIp, dstIp, srcPort, dstPort, writeTun)
        }
        session.lastActivity = System.currentTimeMillis()

        scope.launch {
            try {
                val bypass = DpiBypass(strategy)
                bypass.applyOnUdpSend(
                    session.socket,
                    InetSocketAddress(key.dstIp, key.dstPort),
                    payload
                )
            } catch (e: Exception) {
                onLog("UDP send error ${key.dstIp}:${key.dstPort} - ${e.message}")
                sessions.remove(key)
            }
        }
    }

    private fun createSession(
        key: ConnectionKey,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        writeTun: (ByteArray) -> Unit
    ): UdpSession {
        val sock = DatagramSocket()
        vpnService.protect(sock)

        val job = scope.launch {
            val buf = ByteArray(65507)
            while (isActive) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    withContext(Dispatchers.IO) { sock.receive(pkt) }
                    val data = pkt.data.copyOf(pkt.length)

                    val respPkt = PacketUtils.buildIpUdpPacket(
                        srcIp = dstIp, dstIp = srcIp,
                        srcPort = dstPort, dstPort = srcPort,
                        payload = data
                    )
                    writeTun(respPkt)
                } catch (_: Exception) { break }
            }
        }

        return UdpSession(key, sock, job = job).also { sessions[key] = it }
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (_, session) ->
            if (now - session.lastActivity > SESSION_TIMEOUT_MS) {
                session.job?.cancel()
                try { session.socket.close() } catch (_: Exception) {}
                true
            } else false
        }
    }

    fun shutdown() {
        scope.cancel()
        sessions.values.forEach {
            it.job?.cancel()
            try { it.socket.close() } catch (_: Exception) {}
        }
        sessions.clear()
    }
}
