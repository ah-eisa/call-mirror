package com.eisax.callmirror

import java.net.ServerSocket
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.concurrent.thread

class LocalServer(
    private val port: Int,
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private val seen = Collections.synchronizedMap(object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 512
    })

    fun start() {
        if (running || pairingSecret.length < 16) return
        running = true
        thread(name = "CallMirrorLocalServer", isDaemon = true) {
            try {
                ServerSocket(port).use { server ->
                    serverSocket = server
                    while (running) {
                        val client = try { server.accept() } catch (_: Exception) { break }
                        client.use { socket ->
                            socket.soTimeout = 2000
                            val line = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine() ?: return@use
                            val plain = CryptoUtils.decrypt(line, pairingSecret) ?: return@use
                            val payload = MirrorPayload.fromJson(plain) ?: return@use
                            if (!isFresh(payload.timestamp)) return@use
                            if (seen.putIfAbsent(payload.messageId, System.currentTimeMillis()) != null) return@use
                            onPayload(payload)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                running = false
                serverSocket = null
            }
        }
    }

    fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    private fun isFresh(timestamp: Long): Boolean {
        if (timestamp <= 0L) return false
        val age = kotlin.math.abs(System.currentTimeMillis() - timestamp)
        return age <= 10 * 60 * 1000L
    }
}
