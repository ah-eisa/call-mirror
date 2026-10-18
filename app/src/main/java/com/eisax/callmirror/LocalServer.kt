package com.eisax.callmirror

import java.net.ServerSocket
import java.util.LinkedHashMap
import kotlin.concurrent.thread

class LocalServer(
    private val port: Int,
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit,
    private val replayGuard: MessageGuard = MessageGuard(
        MessageGuard.DEFAULT_MAX_MESSAGE_AGE_MS,
        REPLAY_CAPACITY,
        MessageGuard.DEFAULT_DRIFT_TOLERANCE_MS
    )
) {
    @Volatile private var running = false

    /** Actual bound port; equals [port] unless [port] was 0 (ephemeral). */
    @Volatile var boundPort: Int = -1
        private set

    private var serverSocket: ServerSocket? = null
    private val connectionLimiter = RateLimiter(GLOBAL_MAX_CONNECTIONS, GLOBAL_WINDOW_MS)
    private val ipLimiters = object : LinkedHashMap<String, RateLimiter>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RateLimiter>?): Boolean =
            size > TRACKED_IPS
    }

    fun start() {
        if (running || pairingSecret.length < 16) return
        val server = try { ServerSocket(port) } catch (_: Exception) { return }
        serverSocket = server
        boundPort = server.localPort
        running = true
        thread(name = "CallMirrorLocalServer", isDaemon = true) {
            try {
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
                        acknowledge(socket, payload.messageId)
                    }
                }
            } catch (_: Exception) {
            } finally {
                running = false
                serverSocket = null
                boundPort = -1
            }
        }
    }

    fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    /**
     * Confirms delivery to the sender. The acknowledgement is MAC'd with the CM3
     * ack key, so a peer without the pairing secret cannot forge one and force
     * the sender to skip the cloud fallback.
     */
    private fun acknowledge(socket: java.net.Socket, messageId: String) {
        val ack = CryptoUtils.createAck(pairingSecret, messageId) ?: return
        try {
            socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(ack)
                writer.newLine()
                writer.flush()
            }
        } catch (_: Exception) {
        }
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
        private const val REPLAY_CAPACITY = 512
    }
}
