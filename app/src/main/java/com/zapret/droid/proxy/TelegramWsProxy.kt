package com.zapret.droid.proxy

import android.net.VpnService
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocketFactory

// Telegram DPI bypass via WebSocket tunnel to kws*.web.telegram.org
// Implements the tg-ws-proxy logic:
// 1. Listen on local port 1443 as MTProto proxy
// 2. Intercept MTProto obfuscation handshake (64 bytes)
// 3. Decrypt to get DC id and proto tag
// 4. Open WSS connection to kwsDC.web.telegram.org/apiws
// 5. Tunnel MTProto packets as RFC 6455 binary WebSocket frames
class TelegramWsProxy(
    private val vpnService: VpnService,
    private val onLog: (String) -> Unit
) {
    companion object {
        const val LOCAL_PORT = 1443
        private val DC_HOSTS = mapOf(
            1 to listOf("kws1.web.telegram.org", "kws1-1.web.telegram.org"),
            2 to listOf("kws2.web.telegram.org", "kws2-1.web.telegram.org"),
            3 to listOf("kws3.web.telegram.org", "kws3-1.web.telegram.org"),
            4 to listOf("kws4.web.telegram.org", "kws4-1.web.telegram.org"),
            5 to listOf("kws5.web.telegram.org", "kws5-1.web.telegram.org")
        )
        private const val WS_PATH = "/apiws"
        private const val HANDSHAKE_SIZE = 64
    }

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionPool = Collections.synchronizedMap(mutableMapOf<Int, List<WsConnection>>())

    suspend fun start() {
        try {
            serverSocket = ServerSocket(LOCAL_PORT)
            onLog("Telegram WS proxy listening on port $LOCAL_PORT")
            while (scope.isActive) {
                val client = withContext(Dispatchers.IO) {
                    serverSocket?.accept()
                } ?: break
                scope.launch { handleClient(client) }
            }
        } catch (e: Exception) {
            onLog("Telegram proxy error: ${e.message}")
        }
    }

    suspend fun stop() {
        scope.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private suspend fun handleClient(client: Socket) {
        try {
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // Read MTProto obfuscation handshake (64 bytes)
            val handshake = ByteArray(HANDSHAKE_SIZE)
            var read = 0
            while (read < HANDSHAKE_SIZE) {
                val n = withContext(Dispatchers.IO) { clientIn.read(handshake, read, HANDSHAKE_SIZE - read) }
                if (n < 0) return
                read += n
            }

            // Extract prekey (32 bytes at offset 8) and IV (16 bytes at offset 8+32=40)
            val prekey = handshake.copyOfRange(8, 40)
            val iv = handshake.copyOfRange(40, 56)

            // AES-256-CTR decrypt bytes 56-64 to get DC id and proto tag
            val sha256 = MessageDigest.getInstance("SHA-256")
            sha256.update(prekey)
            sha256.update(byteArrayOf(0x54, 0x47, 0x00, 0x00))  // "TG" + padding
            val key = sha256.digest()

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            // Decrypt whole handshake to extract DC and tag
            val decrypted = cipher.doFinal(handshake)
            val dcIdRaw = ((decrypted[60].toInt() and 0xFF) or ((decrypted[61].toInt() and 0xFF) shl 8)).toShort()
            val dcId = Math.abs(dcIdRaw.toInt()).coerceIn(1, 5)

            onLog("Telegram: client connected, DC=$dcId")

            // Connect to Telegram WS server
            val wsConn = connectToTelegramWs(dcId) ?: run {
                onLog("Telegram: failed to connect WS for DC$dcId")
                client.close()
                return
            }

            // Re-init cipher for encryption of client→server stream
            val encCipher = Cipher.getInstance("AES/CTR/NoPadding")
            encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            // Bidirectional tunnel: client ↔ WS server
            val job1 = scope.launch {
                clientToWs(clientIn, wsConn, encCipher)
            }
            val job2 = scope.launch {
                wsToClient(wsConn, clientOut)
            }

            job1.join()
            job2.join()

        } catch (e: Exception) {
            onLog("Telegram client error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun connectToTelegramWs(dcId: Int): WsConnection? {
        val hosts = DC_HOSTS[dcId] ?: return null
        for (host in hosts) {
            try {
                val sock = SSLSocketFactory.getDefault().createSocket(host, 443) as javax.net.ssl.SSLSocket
                vpnService.protect(sock)
                sock.startHandshake()

                val wsKey = Base64.getEncoder().encodeToString(
                    ByteArray(16).also { Random().nextBytes(it) }
                )
                val request = buildString {
                    append("GET $WS_PATH HTTP/1.1\r\n")
                    append("Host: $host\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $wsKey\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("Sec-WebSocket-Protocol: binary\r\n")
                    append("Origin: https://$host\r\n")
                    append("\r\n")
                }
                sock.outputStream.write(request.toByteArray())
                sock.outputStream.flush()

                // Read HTTP 101 response
                val resp = readHttpResponse(sock.inputStream)
                if (!resp.startsWith("HTTP/1.1 101")) {
                    sock.close()
                    continue
                }

                onLog("Telegram: WS connected to $host")
                return WsConnection(sock, sock.inputStream, sock.outputStream)
            } catch (e: Exception) {
                onLog("Telegram: WS connect to $host failed: ${e.message}")
            }
        }
        return null
    }

    private fun readHttpResponse(input: InputStream): String {
        val sb = StringBuilder()
        var prev = 0
        var prevprev = 0
        var prevprevprev = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
            if (prevprevprev == '\r'.code && prevprev == '\n'.code && prev == '\r'.code && b == '\n'.code) break
            prevprevprev = prevprev; prevprev = prev; prev = b
        }
        return sb.toString()
    }

    private suspend fun clientToWs(input: InputStream, ws: WsConnection, cipher: Cipher) {
        val buf = ByteArray(16384)
        while (scope.isActive) {
            val n = withContext(Dispatchers.IO) {
                try { input.read(buf) } catch (_: Exception) { -1 }
            }
            if (n < 0) break
            val data = cipher.update(buf, 0, n) ?: continue
            ws.sendBinaryFrame(data)
        }
        try { ws.socket.close() } catch (_: Exception) {}
    }

    private suspend fun wsToClient(ws: WsConnection, output: OutputStream) {
        while (scope.isActive) {
            val frame = withContext(Dispatchers.IO) {
                try { ws.receiveFrame() } catch (_: Exception) { null }
            } ?: break
            withContext(Dispatchers.IO) {
                try { output.write(frame); output.flush() } catch (_: Exception) {}
            }
        }
        try { ws.socket.close() } catch (_: Exception) {}
    }

    inner class WsConnection(
        val socket: Socket,
        private val input: InputStream,
        private val output: OutputStream
    ) {
        private val mask = ByteArray(4).also { Random().nextBytes(it) }

        fun sendBinaryFrame(data: ByteArray) {
            val frame = buildWsFrame(data, opcode = 0x02, masked = true)
            synchronized(output) {
                output.write(frame)
                output.flush()
            }
        }

        fun receiveFrame(): ByteArray? {
            val b0 = input.read()
            val b1 = input.read()
            if (b0 < 0 || b1 < 0) return null
            val opcode = b0 and 0x0F
            val isMasked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                len = ((input.read().toLong() shl 8) or input.read().toLong())
            } else if (len == 127L) {
                len = 0
                for (i in 0 until 8) len = (len shl 8) or input.read().toLong()
            }
            val maskBytes = if (isMasked) ByteArray(4).also { input.read(it) } else null
            val payload = ByteArray(len.toInt())
            var readBytes = 0
            while (readBytes < payload.size) {
                val n = input.read(payload, readBytes, payload.size - readBytes)
                if (n < 0) return null
                readBytes += n
            }
            if (maskBytes != null) {
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor maskBytes[i % 4].toInt()).toByte()
            }
            if (opcode == 0x08) return null  // close frame
            return payload
        }

        private fun buildWsFrame(data: ByteArray, opcode: Int, masked: Boolean): ByteArray {
            val len = data.size
            val maskBytes = if (masked) ByteArray(4).also { Random().nextBytes(it) } else null
            val headerSize = 2 + (if (len < 126) 0 else if (len < 65536) 2 else 8) + (if (masked) 4 else 0)
            val frame = ByteArray(headerSize + len)
            var pos = 0
            frame[pos++] = (0x80 or opcode).toByte()
            val maskBit = if (masked) 0x80 else 0x00
            when {
                len < 126 -> frame[pos++] = (maskBit or len).toByte()
                len < 65536 -> {
                    frame[pos++] = (maskBit or 126).toByte()
                    frame[pos++] = (len shr 8).toByte(); frame[pos++] = (len and 0xFF).toByte()
                }
                else -> {
                    frame[pos++] = (maskBit or 127).toByte()
                    for (i in 7 downTo 0) frame[pos++] = ((len.toLong() shr (i * 8)) and 0xFF).toByte()
                }
            }
            if (maskBytes != null) {
                maskBytes.copyInto(frame, pos); pos += 4
            }
            for (i in data.indices) {
                frame[pos + i] = if (maskBytes != null) (data[i].toInt() xor maskBytes[i % 4].toInt()).toByte() else data[i]
            }
            return frame
        }
    }
}
