package com.zapret.droid.proxy

import java.net.InetAddress
import java.nio.ByteBuffer

object PacketUtils {

    fun parseIpVersion(buf: ByteArray): Int = (buf[0].toInt() and 0xFF) shr 4

    fun parseIpProtocol(buf: ByteArray): Int = buf[9].toInt() and 0xFF

    fun parseIpSrc(buf: ByteArray): ByteArray = buf.copyOfRange(12, 16)

    fun parseIpDst(buf: ByteArray): ByteArray = buf.copyOfRange(16, 20)

    fun parseIpHeaderLen(buf: ByteArray): Int = (buf[0].toInt() and 0x0F) * 4

    fun parseTcpSrcPort(buf: ByteArray, ipHL: Int): Int =
        ((buf[ipHL].toInt() and 0xFF) shl 8) or (buf[ipHL + 1].toInt() and 0xFF)

    fun parseTcpDstPort(buf: ByteArray, ipHL: Int): Int =
        ((buf[ipHL + 2].toInt() and 0xFF) shl 8) or (buf[ipHL + 3].toInt() and 0xFF)

    fun parseTcpSeq(buf: ByteArray, ipHL: Int): Long {
        val bb = ByteBuffer.wrap(buf, ipHL + 4, 4)
        return bb.int.toLong() and 0xFFFFFFFFL
    }

    fun parseTcpAck(buf: ByteArray, ipHL: Int): Long {
        val bb = ByteBuffer.wrap(buf, ipHL + 8, 4)
        return bb.int.toLong() and 0xFFFFFFFFL
    }

    fun parseTcpFlags(buf: ByteArray, ipHL: Int): Int = buf[ipHL + 13].toInt() and 0xFF

    fun parseTcpHeaderLen(buf: ByteArray, ipHL: Int): Int =
        ((buf[ipHL + 12].toInt() and 0xF0) shr 4) * 4

    fun parseTcpPayload(buf: ByteArray, ipHL: Int): ByteArray {
        val tcpHL = parseTcpHeaderLen(buf, ipHL)
        val start = ipHL + tcpHL
        return if (start < buf.size) buf.copyOfRange(start, buf.size) else ByteArray(0)
    }

    fun parseUdpSrcPort(buf: ByteArray, ipHL: Int): Int =
        ((buf[ipHL].toInt() and 0xFF) shl 8) or (buf[ipHL + 1].toInt() and 0xFF)

    fun parseUdpDstPort(buf: ByteArray, ipHL: Int): Int =
        ((buf[ipHL + 2].toInt() and 0xFF) shl 8) or (buf[ipHL + 3].toInt() and 0xFF)

    fun parseUdpPayload(buf: ByteArray, ipHL: Int): ByteArray =
        buf.copyOfRange(ipHL + 8, buf.size)

    fun buildIpTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0),
        ttl: Int = 64
    ): ByteArray {
        val tcpLen = 20 + payload.size
        val totalLen = 20 + tcpLen

        val buf = ByteArray(totalLen)
        // IP header
        buf[0] = 0x45.toByte()                                        // version=4, IHL=5
        buf[1] = 0x00
        buf[2] = ((totalLen shr 8) and 0xFF).toByte()
        buf[3] = (totalLen and 0xFF).toByte()
        buf[4] = 0x00; buf[5] = 0x00                                  // id
        buf[6] = 0x40; buf[7] = 0x00                                  // flags=DF, frag=0
        buf[8] = ttl.toByte()
        buf[9] = 0x06                                                  // protocol TCP
        buf[10] = 0x00; buf[11] = 0x00                                // checksum (calculated later)
        srcIp.copyInto(buf, 12)
        dstIp.copyInto(buf, 16)

        // TCP header
        val tcpOff = 20
        buf[tcpOff] = ((srcPort shr 8) and 0xFF).toByte()
        buf[tcpOff + 1] = (srcPort and 0xFF).toByte()
        buf[tcpOff + 2] = ((dstPort shr 8) and 0xFF).toByte()
        buf[tcpOff + 3] = (dstPort and 0xFF).toByte()
        putUInt32(buf, tcpOff + 4, seq)
        putUInt32(buf, tcpOff + 8, ack)
        buf[tcpOff + 12] = 0x50.toByte()                             // data offset=5
        buf[tcpOff + 13] = flags.toByte()
        buf[tcpOff + 14] = ((windowSize shr 8) and 0xFF).toByte()
        buf[tcpOff + 15] = (windowSize and 0xFF).toByte()
        buf[tcpOff + 16] = 0x00; buf[tcpOff + 17] = 0x00            // checksum
        buf[tcpOff + 18] = 0x00; buf[tcpOff + 19] = 0x00            // urgent

        payload.copyInto(buf, tcpOff + 20)

        // compute checksums
        putUInt16(buf, 10, ipChecksum(buf, 0, 20))
        putUInt16(buf, tcpOff + 16, tcpChecksum(buf, srcIp, dstIp, tcpOff, tcpLen))

        return buf
    }

    fun buildIpUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        ttl: Int = 64
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val buf = ByteArray(totalLen)

        buf[0] = 0x45.toByte()
        buf[1] = 0x00
        buf[2] = ((totalLen shr 8) and 0xFF).toByte()
        buf[3] = (totalLen and 0xFF).toByte()
        buf[4] = 0x00; buf[5] = 0x00
        buf[6] = 0x40; buf[7] = 0x00
        buf[8] = ttl.toByte()
        buf[9] = 0x11                                                  // UDP
        buf[10] = 0x00; buf[11] = 0x00
        srcIp.copyInto(buf, 12)
        dstIp.copyInto(buf, 16)

        val udpOff = 20
        buf[udpOff] = ((srcPort shr 8) and 0xFF).toByte()
        buf[udpOff + 1] = (srcPort and 0xFF).toByte()
        buf[udpOff + 2] = ((dstPort shr 8) and 0xFF).toByte()
        buf[udpOff + 3] = (dstPort and 0xFF).toByte()
        buf[udpOff + 4] = ((udpLen shr 8) and 0xFF).toByte()
        buf[udpOff + 5] = (udpLen and 0xFF).toByte()
        buf[udpOff + 6] = 0x00; buf[udpOff + 7] = 0x00

        payload.copyInto(buf, udpOff + 8)

        putUInt16(buf, 10, ipChecksum(buf, 0, 20))
        putUInt16(buf, udpOff + 6, udpChecksum(buf, srcIp, dstIp, udpOff, udpLen))

        return buf
    }

    private fun ipChecksum(buf: ByteArray, offset: Int, len: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (len % 2 != 0) sum += (buf[offset + len - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun tcpChecksum(buf: ByteArray, srcIp: ByteArray, dstIp: ByteArray, tcpOff: Int, tcpLen: Int): Int {
        val pseudo = ByteArray(12 + tcpLen)
        srcIp.copyInto(pseudo, 0)
        dstIp.copyInto(pseudo, 4)
        pseudo[8] = 0x00
        pseudo[9] = 0x06
        pseudo[10] = ((tcpLen shr 8) and 0xFF).toByte()
        pseudo[11] = (tcpLen and 0xFF).toByte()
        buf.copyInto(pseudo, 12, tcpOff, tcpOff + tcpLen)
        return ipChecksum(pseudo, 0, pseudo.size)
    }

    private fun udpChecksum(buf: ByteArray, srcIp: ByteArray, dstIp: ByteArray, udpOff: Int, udpLen: Int): Int {
        val pseudo = ByteArray(12 + udpLen)
        srcIp.copyInto(pseudo, 0)
        dstIp.copyInto(pseudo, 4)
        pseudo[8] = 0x00
        pseudo[9] = 0x11
        pseudo[10] = ((udpLen shr 8) and 0xFF).toByte()
        pseudo[11] = (udpLen and 0xFF).toByte()
        buf.copyInto(pseudo, 12, udpOff, udpOff + udpLen)
        return ipChecksum(pseudo, 0, pseudo.size)
    }

    private fun putUInt32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun putUInt16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    fun ipToBytes(ip: String): ByteArray = InetAddress.getByName(ip).address

    fun bytesToIp(b: ByteArray): String = InetAddress.getByAddress(b).hostAddress ?: ""

    // TCP flags
    const val FLAG_FIN = 0x01
    const val FLAG_SYN = 0x02
    const val FLAG_RST = 0x04
    const val FLAG_PSH = 0x08
    const val FLAG_ACK = 0x10
    const val FLAG_URG = 0x20
}
