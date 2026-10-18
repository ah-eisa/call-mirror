package com.eisax.callmirror

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sends one payload to a peer on the local network and waits for the peer's
 * authenticated acknowledgement. A `true` result means the peer confirmed
 * receipt, decryption and delivery; anything else (unreachable peer, timeout,
 * missing or forged acknowledgement) leaves cloud relay as the fallback.
 */
object LocalTransport {
    private const val CONNECT_TIMEOUT_MS = 1500
    private const val READ_TIMEOUT_MS = 2500
    private const val MAX_ACK_CHARS = 256

    fun send(host: String, port: Int, pairingSecret: String, payload: MirrorPayload): Boolean {
        if (host.isBlank() || pairingSecret.length < 16) return false
        val encrypted = CryptoUtils.encrypt(payload.toJson(), pairingSecret) ?: return false
        val ack = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(encrypted)
                    writer.newLine()
                    writer.flush()
                }
                readAckLine(socket.getInputStream())
            }
        } catch (_: Exception) {
            null
        }
        return ack != null && CryptoUtils.verifyAck(pairingSecret, ack, payload.messageId)
    }

    private fun readAckLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0 || byte == '\n'.code) break
            if (byte !in 0x20..0x7E || line.length >= MAX_ACK_CHARS) return null
            line.append(byte.toChar())
        }
        return line.toString().takeIf { it.isNotEmpty() }
    }
}
