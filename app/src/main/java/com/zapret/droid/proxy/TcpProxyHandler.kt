package com.zapret.droid.proxy

import android.net.VpnService
import com.zapret.droid.strategies.Strategy
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

// Handles TCP connections intercepted from the TUN interface.
// Each connection is proxied to the real destination with DPI bypass applied.
class TcpProxyHandler(
    private val vpnService: VpnService,
    private val strategy: Strategy,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeConnections = ConcurrentHashMap<ConnectionKey, TcpSession>()

    data class TcpSession(
        val key: ConnectionKey,
        var clientSeq: Long,
        var serverSeq: Long,
        var state: State,
        val realSocket: Socket? = null,
        val job: Job? = null
    )

    enum class State { SYN_RECEIVED, ESTABLISHED, CLOSING, CLOSED }

    // Called by TunProxy when a TCP packet arrives from the TUN device
    // Returns packets to write back to TUN (responses to client)
    fun handlePacket(
        buf: ByteArray,
        writeTun: (ByteArray) -> Unit
    ) {
        val ipHL = PacketUtils.parseIpHeaderLen(buf)
        val srcIp = PacketUtils.parseIpSrc(buf)
        val dstIp = PacketUtils.parseIpDst(buf)
        val srcPort = PacketUtils.parseTcpSrcPort(buf, ipHL)
        val dstPort = PacketUtils.parseTcpDstPort(buf, ipHL)
        val seq = PacketUtils.parseTcpSeq(buf, ipHL)
        val ack = PacketUtils.parseTcpAck(buf, ipHL)
        val flags = PacketUtils.parseTcpFlags(buf, ipHL)
        val payload = PacketUtils.parseTcpPayload(buf, ipHL)

        val key = ConnectionKey(
            PacketUtils.bytesToIp(srcIp), srcPort,
            PacketUtils.bytesToIp(dstIp), dstPort,
            6
        )

        when {
            flags and PacketUtils.FLAG_SYN != 0 && flags and PacketUtils.FLAG_ACK == 0 -> {
                // SYN: new connection
                handleSyn(key, seq, srcIp, dstIp, srcPort, dstPort, writeTun)
            }

            flags and PacketUtils.FLAG_ACK != 0 && payload.isNotEmpty() -> {
                // Data packet
                activeConnections[key]?.let { session ->
                    if (session.state == State.ESTABLISHED) {
                        session.realSocket?.let { sock ->
                            scope.launch {
                                try {
                                    val bypass = DpiBypass(strategy)
                                    if (session.clientSeq == seq) {
                                        // First data (TLS ClientHello)
                                        bypass.applyOnConnect(sock, payload, sock.getOutputStream())
                                    } else {
                                        sock.getOutputStream().write(payload)
                                        sock.getOutputStream().flush()
                                    }
                                    session.clientSeq = (seq + payload.size) and 0xFFFFFFFFL
                                    // Send ACK back to client
                                    val ackPkt = PacketUtils.buildIpTcpPacket(
                                        srcIp = dstIp, dstIp = srcIp,
                                        srcPort = dstPort, dstPort = srcPort,
                                        seq = session.serverSeq,
                                        ack = session.clientSeq,
                                        flags = PacketUtils.FLAG_ACK
                                    )
                                    writeTun(ackPkt)
                                } catch (e: Exception) {
                                    onLog("TCP data error ${key.dstIp}:${key.dstPort} - ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            flags and PacketUtils.FLAG_FIN != 0 -> {
                activeConnections.remove(key)?.let { session ->
                    scope.launch {
                        try {
                            session.realSocket?.close()
                        } catch (_: Exception) {}
                    }
                    val finAck = PacketUtils.buildIpTcpPacket(
                        srcIp = dstIp, dstIp = srcIp,
                        srcPort = dstPort, dstPort = srcPort,
                        seq = session.serverSeq,
                        ack = (seq + 1) and 0xFFFFFFFFL,
                        flags = PacketUtils.FLAG_FIN or PacketUtils.FLAG_ACK
                    )
                    writeTun(finAck)
                }
            }

            flags and PacketUtils.FLAG_RST != 0 -> {
                activeConnections.remove(key)?.let { session ->
                    scope.launch { try { session.realSocket?.close() } catch (_: Exception) {} }
                }
            }
        }
    }

    private fun handleSyn(
        key: ConnectionKey,
        clientSeq: Long,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        writeTun: (ByteArray) -> Unit
    ) {
        scope.launch {
            try {
                val realSocket = Socket()
                vpnService.protect(realSocket)
                realSocket.tcpNoDelay = true
                realSocket.connect(InetSocketAddress(key.dstIp, key.dstPort), 10_000)

                val serverIsn = (Math.random() * 0xFFFFFFFFL).toLong()
                val session = TcpSession(
                    key = key,
                    clientSeq = (clientSeq + 1) and 0xFFFFFFFFL,
                    serverSeq = (serverIsn + 1) and 0xFFFFFFFFL,
                    state = State.ESTABLISHED,
                    realSocket = realSocket
                )
                activeConnections[key] = session

                // Send SYN-ACK to client
                val synAck = PacketUtils.buildIpTcpPacket(
                    srcIp = dstIp, dstIp = srcIp,
                    srcPort = dstPort, dstPort = srcPort,
                    seq = serverIsn,
                    ack = (clientSeq + 1) and 0xFFFFFFFFL,
                    flags = PacketUtils.FLAG_SYN or PacketUtils.FLAG_ACK,
                    windowSize = 65535
                )
                writeTun(synAck)

                // Start reading from real server and forwarding to TUN client
                launch {
                    forwardServerToClient(session, srcIp, dstIp, srcPort, dstPort, writeTun)
                }
            } catch (e: Exception) {
                onLog("Connect failed to ${key.dstIp}:${key.dstPort} - ${e.message}")
                // Send RST to client
                val rst = PacketUtils.buildIpTcpPacket(
                    srcIp = dstIp, dstIp = srcIp,
                    srcPort = dstPort, dstPort = srcPort,
                    seq = 0, ack = (clientSeq + 1) and 0xFFFFFFFFL,
                    flags = PacketUtils.FLAG_RST or PacketUtils.FLAG_ACK
                )
                writeTun(rst)
            }
        }
    }

    private suspend fun forwardServerToClient(
        session: TcpSession,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        writeTun: (ByteArray) -> Unit
    ) {
        val sock = session.realSocket ?: return
        val bufArray = ByteArray(16384)
        val input = sock.getInputStream()
        try {
            while (true) {
                val n = withContext(Dispatchers.IO) { input.read(bufArray) }
                if (n < 0) break
                val data = bufArray.copyOf(n)

                val dataPkt = PacketUtils.buildIpTcpPacket(
                    srcIp = dstIp, dstIp = srcIp,
                    srcPort = dstPort, dstPort = srcPort,
                    seq = session.serverSeq,
                    ack = session.clientSeq,
                    flags = PacketUtils.FLAG_PSH or PacketUtils.FLAG_ACK,
                    payload = data
                )
                session.serverSeq = (session.serverSeq + n) and 0xFFFFFFFFL
                writeTun(dataPkt)
            }
        } catch (_: Exception) {}

        // Send FIN to client
        activeConnections.remove(session.key)
        val finPkt = PacketUtils.buildIpTcpPacket(
            srcIp = dstIp, dstIp = srcIp,
            srcPort = dstPort, dstPort = srcPort,
            seq = session.serverSeq,
            ack = session.clientSeq,
            flags = PacketUtils.FLAG_FIN or PacketUtils.FLAG_ACK
        )
        writeTun(finPkt)
        try { sock.close() } catch (_: Exception) {}
    }

    fun shutdown() {
        scope.cancel()
        activeConnections.values.forEach {
            try { it.realSocket?.close() } catch (_: Exception) {}
        }
        activeConnections.clear()
    }
}
