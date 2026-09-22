package com.zapret.droid.proxy

import com.zapret.droid.strategies.*
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket

class DpiBypass(private val strategy: Strategy) {

    fun applyOnConnect(socket: Socket, clientData: ByteArray, out: OutputStream) {
        socket.tcpNoDelay = true

        val methods = strategy.dpiDesync

        when {
            methods.contains(DesyncMethod.SYNDATA) ->
                sendSplit(out, clientData, 1)

            methods.contains(DesyncMethod.MULTIDISORDER) ->
                sendDisorder(socket, out, clientData, getSplitPosition(clientData, strategy.splitPos))

            methods.contains(DesyncMethod.HOSTFAKESPLIT) ->
                sendHostFakeSplit(socket, out, clientData, getSplitPosition(clientData, strategy.splitPos))

            methods.contains(DesyncMethod.FAKE) && methods.contains(DesyncMethod.MULTISPLIT) -> {
                sendFakePackets(socket, out)
                sendSplitWithSeqovl(socket, out, clientData, getSplitPosition(clientData, strategy.splitPos))
            }

            methods.contains(DesyncMethod.FAKE) && methods.contains(DesyncMethod.FAKEDSPLIT) -> {
                sendFakePackets(socket, out)
                sendDisorder(socket, out, clientData, getSplitPosition(clientData, strategy.splitPos))
            }

            methods.contains(DesyncMethod.MULTISPLIT) ->
                sendSplitWithSeqovl(socket, out, clientData, getSplitPosition(clientData, strategy.splitPos))

            methods.contains(DesyncMethod.FAKE) -> {
                sendFakePackets(socket, out)
                out.write(clientData)
                out.flush()
            }

            else -> {
                out.write(clientData)
                out.flush()
            }
        }
    }

    fun applyOnUdpSend(socket: DatagramSocket, realDst: InetSocketAddress, payload: ByteArray) {
        if (strategy.dpiDesync.contains(DesyncMethod.FAKE)) {
            val fakeData = getFakeUdpData()
            // Use MulticastSocket cast to set TTL; fallback to plain send if unavailable
            val mc = socket as? MulticastSocket
            repeat(strategy.dpiDesyncRepeats) {
                try {
                    mc?.timeToLive = strategy.fakeTtl
                    socket.send(DatagramPacket(fakeData, fakeData.size, realDst))
                } catch (_: Exception) {}
            }
            try { mc?.timeToLive = 64 } catch (_: Exception) {}
        }
        socket.send(DatagramPacket(payload, payload.size, realDst))
    }

    private fun setTtl(socket: Socket, ttl: Int) {
        try {
            // Reflection-based IP_TTL for Android (no StandardSocketOptions available pre-API24)
            val implField = Socket::class.java.getDeclaredField("impl")
            implField.isAccessible = true
            val impl = implField.get(socket)
            val setOptMethod = impl.javaClass.getMethod("setOption", Int::class.java, Any::class.java)
            setOptMethod.invoke(impl, 0x03 /* IP_TTL */, ttl)
        } catch (_: Exception) {}
    }

    private fun sendFakePackets(socket: Socket, out: OutputStream) {
        val fakeData = getFakeTcpData()
        repeat(strategy.dpiDesyncRepeats) {
            try {
                setTtl(socket, strategy.fakeTtl)
                out.write(fakeData)
                out.flush()
            } catch (_: Exception) {}
        }
        setTtl(socket, 64)
    }

    private fun sendSplit(out: OutputStream, data: ByteArray, splitAt: Int) {
        val pos = splitAt.coerceIn(1, data.size - 1)
        out.write(data, 0, pos)
        out.flush()
        Thread.sleep(1)
        out.write(data, pos, data.size - pos)
        out.flush()
    }

    private fun sendSplitWithSeqovl(socket: Socket, out: OutputStream, data: ByteArray, splitAt: Int) {
        val fakeChunk = getFakeTcpData().copyOf(splitAt.coerceAtLeast(1))
        try {
            setTtl(socket, strategy.fakeTtl)
            out.write(fakeChunk)
            out.flush()
            setTtl(socket, 64)
        } catch (_: Exception) {}

        val pos = splitAt.coerceIn(1, data.size - 1)
        out.write(data, 0, pos)
        out.flush()
        Thread.sleep(1)
        out.write(data, pos, data.size - pos)
        out.flush()
    }

    private fun sendDisorder(socket: Socket, out: OutputStream, data: ByteArray, splitAt: Int) {
        val pos = splitAt.coerceIn(1, data.size - 1)
        val first = data.copyOfRange(0, pos)
        val second = data.copyOfRange(pos, data.size)
        try {
            setTtl(socket, strategy.fakeTtl)
            out.write(second)
            out.flush()
            setTtl(socket, 64)
        } catch (_: Exception) {}
        Thread.sleep(1)
        out.write(first)
        out.flush()
        Thread.sleep(1)
        out.write(second)
        out.flush()
    }

    private fun sendHostFakeSplit(socket: Socket, out: OutputStream, data: ByteArray, splitAt: Int) {
        try {
            setTtl(socket, strategy.fakeTtl)
            out.write("GET / HTTP/1.1\r\nHost: www.google.com\r\n\r\n".toByteArray())
            out.flush()
            setTtl(socket, 64)
        } catch (_: Exception) {}
        sendSplit(out, data, splitAt)
    }

    private fun getSplitPosition(data: ByteArray, pos: SplitPos): Int = when (pos) {
        SplitPos.POS_1 -> 1
        SplitPos.POS_2 -> 2
        SplitPos.MIDSLD -> FakePackets.findSniMidSld(data)
        SplitPos.SNIEXT_PLUS_1 -> {
            val off = FakePackets.findSniOffset(data)
            if (off > 0) off + 1 else data.size / 2
        }
    }

    private fun getFakeTcpData(): ByteArray = when (strategy.fakeTlsMod) {
        FakeTlsMod.RND -> FakePackets.buildTlsClientHello(strategy.fakeTlsSni, randomize = true)
        FakeTlsMod.DUPSID -> FakePackets.buildTlsClientHello(strategy.fakeTlsSni, dupSid = true)
        FakeTlsMod.NONE -> when (strategy.fakeData) {
            FakeData.QUIC_GOOGLE -> FakePackets.QUIC_INITIAL_GOOGLE
            FakeData.QUIC_DBANK -> FakePackets.QUIC_INITIAL_DBANK
            FakeData.TLS_GOOGLE -> FakePackets.TLS_CLIENT_HELLO_GOOGLE
            FakeData.TLS_MAX -> FakePackets.TLS_CLIENT_HELLO_MAX
            FakeData.TLS_4PDA -> FakePackets.TLS_CLIENT_HELLO_4PDA
            FakeData.STUN -> FakePackets.STUN_BINDING
            FakeData.UNKNOWN_UDP -> FakePackets.UNKNOWN_UDP
        }
    }

    private fun getFakeUdpData(): ByteArray = when (strategy.fakeData) {
        FakeData.QUIC_GOOGLE -> FakePackets.QUIC_INITIAL_GOOGLE
        FakeData.QUIC_DBANK -> FakePackets.QUIC_INITIAL_DBANK
        FakeData.STUN -> FakePackets.STUN_BINDING
        else -> FakePackets.UNKNOWN_UDP
    }
}
