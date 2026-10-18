package com.eisax.callmirror

import java.net.InetSocketAddress
import java.net.Socket

object LocalTransport {
    fun send(host: String, port: Int, pairingSecret: String, payload: MirrorPayload): Boolean {
        if (host.isBlank() || pairingSecret.length < 16) return false
        val encrypted = CryptoUtils.encrypt(payload.toJson(), pairingSecret) ?: return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
                socket.soTimeout = 1500
                socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(encrypted)
                    writer.newLine()
                    writer.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
