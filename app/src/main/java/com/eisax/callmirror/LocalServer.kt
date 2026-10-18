package com.eisax.callmirror

import java.net.ServerSocket
import java.util.LinkedHashMap
import kotlin.concurrent.thread

class LocalServer(
    private val port: Int,
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private val replayGuard = MessageGuard(10 * 60 * 1000L, 512)
    private val connectionLimiter = RateLimiter(GLOBAL_MAX_CONNECTIONS, GLOBAL_WINDOW_MS)
    private val ipLimiters = object : LinkedHashMap<String, RateLimiter>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RateLimiter>?): Boolean =
            size > TRACKED_IPS
    }

    fun start() {
        if (running || pairingSecret.length < 16) return
        running = true
        thread(name = "CallMirrorLocalServer", isDaemon = true) {
            try {
                ServerSocket(port).use { server ->
                    serverSocket = server
                    while (running) {
                        val client = try { server.accept() } catch (_: Exception) { break }
                        val remoteIp = client.inetAddress?.hostAddress ?: "unknown"
                        val nowMs = System.currentTimeMillis()
                        if (!connectionLimiter.tryAcquire(nowMs) ||
                            !synchronizedLimiter(remoteIp).tryAcquire(nowMs)
                        ) {
                            client.close()
                            continue
                        }
                        client.use { socket ->
                            socket.soTimeout = 2000
                            val line = readPayloadLine(socket) ?: return@use
                            val plain = CryptoUtils.decrypt(line, pairingSecret) ?: return@use
                            val payload = MirrorPayload.fromJson(plain) ?: return@use
                            if (!replayGuard.accept(payload.timestamp, payload.messageId)) return@use
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

    private fun synchronizedLimiter(remoteIp: String): RateLimiter {
        synchronized(ipLimiters) {
            return ipLimiters.getOrPut(remoteIp) { RateLimiter(PER_IP_MAX_CONNECTIONS, PER_IP_WINDOW_MS) }
        }
    }

    /**
     * Reads one terminated line with a hard size cap and ASCII-only payload
     * characters, so a peer cannot stream an unbounded line to stall the loop.
     */
    private fun readPayloadLine(socket: java.net.Socket): String? {
        val input = socket.getInputStream()
        val payload = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0 || byte == '\n'.code) break
            if (byte !in 0x20..0x7E || payload.length >= MAX_PAYLOAD_CHARS) return null
            payload.append(byte.toChar())
        }
        return payload.toString().takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val MAX_PAYLOAD_CHARS = 8192
        private const val GLOBAL_MAX_CONNECTIONS = 300
        private const val GLOBAL_WINDOW_MS = 60_000L
        private const val PER_IP_MAX_CONNECTIONS = 30
        private const val PER_IP_WINDOW_MS = 60_000L
        private const val TRACKED_IPS = 64
    }
}
