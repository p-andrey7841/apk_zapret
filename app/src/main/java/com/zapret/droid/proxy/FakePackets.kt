package com.zapret.droid.proxy

// Pre-recorded fake packet payloads for DPI bypass.
// These are standard handshake bytes used as decoys sent with TTL=1
// so they reach DPI sensors but die before the real server.
object FakePackets {

    // Minimal QUIC Initial packet targeting www.google.com
    val QUIC_INITIAL_GOOGLE: ByteArray = byteArrayOf(
        0xC0.toByte(), 0x00, 0x00, 0x00, 0x01,   // Long header, version 1
        0x08,                                       // DCIL
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // dest conn id (8 bytes)
        0x00,                                       // SCIL
        0x00,                                       // token length
        0x40, 0x1C,                                // packet length (varint)
        0x00, 0x00, 0x00, 0x01,                   // packet number
        // Padded CRYPTO frame placeholder
        0x06, 0x00, 0x10,
        // Fake ClientHello fragment (SNI=www.google.com prefix)
        0x01, 0x00, 0x00, 0x0C,
        0x03, 0x03,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00
    )

    // QUIC Initial with dbank pattern (used for Discord/STUN)
    val QUIC_INITIAL_DBANK: ByteArray = byteArrayOf(
        0xC0.toByte(), 0x00, 0x00, 0x00, 0x01,
        0x08,
        0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte(), 0xFC.toByte(),
        0xFB.toByte(), 0xFA.toByte(), 0xF9.toByte(), 0xF8.toByte(),
        0x00, 0x00, 0x40, 0x1C,
        0x00, 0x00, 0x00, 0x01,
        0x06, 0x00, 0x10,
        0x01, 0x00, 0x00, 0x0C,
        0x03, 0x03,
        0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
        0xEE.toByte(), 0xFF.toByte(), 0x11, 0x22,
        0x33, 0x44, 0x55, 0x66,
        0x77, 0x88.toByte(), 0x99.toByte(), 0xAA.toByte(),
        0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(),
        0xFF.toByte(), 0x00, 0x11, 0x22,
        0x33, 0x44, 0x55, 0x66
    )

    // Fake TLS ClientHello targeting www.google.com
    val TLS_CLIENT_HELLO_GOOGLE: ByteArray = buildTlsClientHello("www.google.com")

    // Fake TLS ClientHello targeting max.ru (alternative pattern)
    val TLS_CLIENT_HELLO_MAX: ByteArray = buildTlsClientHello("www.max.ru")

    // Fake TLS ClientHello targeting 4pda.to
    val TLS_CLIENT_HELLO_4PDA: ByteArray = buildTlsClientHello("4pda.to")

    // Minimal STUN packet (Binding Request)
    val STUN_BINDING: ByteArray = byteArrayOf(
        0x00, 0x01,                                // message type: Binding Request
        0x00, 0x00,                                // message length: 0
        0x21, 0x12, 0xA4.toByte(), 0x42,          // magic cookie
        0x00, 0x01, 0x02, 0x03,
        0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B                    // transaction id
    )

    // Generic unknown UDP payload
    val UNKNOWN_UDP: ByteArray = ByteArray(16) { 0x00 }

    fun buildTlsClientHello(sni: String, randomize: Boolean = false, dupSid: Boolean = false): ByteArray {
        val sniBytes = sni.toByteArray(Charsets.US_ASCII)
        val sniLen = sniBytes.size
        val sniExtLen = 5 + sniLen       // server_name_list_len(2) + type(1) + name_len(2) + name
        val sniExtTotal = 4 + sniExtLen  // ext_type(2) + ext_len(2) + content

        val random = if (randomize) {
            ByteArray(32).also { java.util.Random().nextBytes(it) }
        } else {
            ByteArray(32) { (it + 1).toByte() }
        }

        val sessionId: ByteArray = if (dupSid) ByteArray(32) { 0xAA.toByte() } else ByteArray(0)
        val sessionIdLen = sessionId.size

        // Cipher suites: TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
        val ciphers = byteArrayOf(
            0x13, 0x01, 0x13, 0x02, 0x13, 0x03,
            0xC0.toByte(), 0x2B.toByte(), 0xC0.toByte(), 0x2C.toByte(),
            0xC0.toByte(), 0x2F.toByte(), 0xC0.toByte(), 0x30.toByte()
        )

        // Extensions: SNI + supported_versions + supported_groups (minimal)
        val extensions = buildSniExtension(sniBytes) +
                byteArrayOf(
                    // supported_versions
                    0x00, 0x2B, 0x00, 0x05, 0x04, 0x03, 0x04, 0x03, 0x03,
                    // supported_groups
                    0x00, 0x0A, 0x00, 0x04, 0x00, 0x02, 0x00, 0x1D,
                    // session_ticket (empty)
                    0x00, 0x23, 0x00, 0x00
                )

        val extLen = extensions.size
        val handshakeBodyLen = (2 + 32 + 1 + sessionIdLen + 2 + ciphers.size + 1 + 1 + 2 + extLen)
        val recordLen = 4 + handshakeBodyLen

        val buf = ByteArray(5 + recordLen)
        var pos = 0

        // TLS Record Header
        buf[pos++] = 0x16  // content_type: handshake
        buf[pos++] = 0x03; buf[pos++] = 0x01  // version: TLS 1.0
        buf[pos++] = ((recordLen shr 8) and 0xFF).toByte()
        buf[pos++] = (recordLen and 0xFF).toByte()

        // Handshake Header
        buf[pos++] = 0x01  // type: ClientHello
        buf[pos++] = 0x00
        buf[pos++] = ((handshakeBodyLen shr 8) and 0xFF).toByte()
        buf[pos++] = (handshakeBodyLen and 0xFF).toByte()

        // ClientHello body
        buf[pos++] = 0x03; buf[pos++] = 0x03  // version: TLS 1.2
        random.copyInto(buf, pos); pos += 32   // random

        buf[pos++] = sessionIdLen.toByte()
        if (sessionIdLen > 0) { sessionId.copyInto(buf, pos); pos += sessionIdLen }

        buf[pos++] = ((ciphers.size shr 8) and 0xFF).toByte()
        buf[pos++] = (ciphers.size and 0xFF).toByte()
        ciphers.copyInto(buf, pos); pos += ciphers.size

        buf[pos++] = 0x01  // compression methods count
        buf[pos++] = 0x00  // no compression

        buf[pos++] = ((extLen shr 8) and 0xFF).toByte()
        buf[pos++] = (extLen and 0xFF).toByte()
        extensions.copyInto(buf, pos)

        return buf
    }

    private fun buildSniExtension(sniBytes: ByteArray): ByteArray {
        val nameLen = sniBytes.size
        val listLen = 3 + nameLen
        val extLen = 2 + listLen
        val buf = ByteArray(4 + extLen)
        var pos = 0
        buf[pos++] = 0x00; buf[pos++] = 0x00  // ext type: server_name
        buf[pos++] = ((extLen shr 8) and 0xFF).toByte()
        buf[pos++] = (extLen and 0xFF).toByte()
        buf[pos++] = ((listLen shr 8) and 0xFF).toByte()
        buf[pos++] = (listLen and 0xFF).toByte()
        buf[pos++] = 0x00  // name_type: host_name
        buf[pos++] = ((nameLen shr 8) and 0xFF).toByte()
        buf[pos++] = (nameLen and 0xFF).toByte()
        sniBytes.copyInto(buf, pos)
        return buf
    }

    // Find SNI extension offset in a TLS ClientHello (returns offset into payload, or -1)
    fun findSniOffset(data: ByteArray): Int {
        if (data.size < 5) return -1
        val recordType = data[0].toInt() and 0xFF
        if (recordType != 0x16) return -1
        // skip record header (5) + handshake header (4) + version (2) + random (32) + session id len(1)
        var pos = 5 + 4 + 2 + 32
        if (pos >= data.size) return -1
        val sidLen = data[pos].toInt() and 0xFF
        pos += 1 + sidLen + 2  // skip session id + cipher suites length
        if (pos >= data.size) return -1
        val csLen = ((data[pos - 2].toInt() and 0xFF) shl 8) or (data[pos - 1].toInt() and 0xFF)
        pos += csLen + 1  // skip cipher suites + compression methods length
        if (pos >= data.size) return -1
        val cmLen = data[pos - 1].toInt() and 0xFF
        pos += cmLen + 2  // skip compression methods + extensions length
        val endExtensions = data.size

        while (pos + 4 <= endExtensions) {
            val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            if (extType == 0x0000) {
                // SNI extension found; SNI content starts at pos+4
                return pos + 4
            }
            pos += 4 + extLen
        }
        return -1
    }

    // Find midpoint of second-level domain in SNI
    fun findSniMidSld(data: ByteArray): Int {
        val sniOff = findSniOffset(data)
        if (sniOff < 0 || sniOff + 5 > data.size) return data.size / 2
        // server_name_list_len(2) + name_type(1) + name_len(2) + name
        val nameLen = ((data[sniOff + 3].toInt() and 0xFF) shl 8) or (data[sniOff + 4].toInt() and 0xFF)
        val nameStart = sniOff + 5
        if (nameStart + nameLen > data.size) return data.size / 2
        val name = String(data, nameStart, nameLen, Charsets.US_ASCII)
        val dotIdx = name.indexOf('.')
        val mid = if (dotIdx >= 0) nameStart + dotIdx / 2 else nameStart + nameLen / 2
        return mid
    }
}
