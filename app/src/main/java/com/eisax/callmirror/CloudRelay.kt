package com.eisax.callmirror

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class CloudRelay(
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit,
    private val onConnectionChanged: ((Boolean) -> Unit)? = null,
    private val replayGuard: MessageGuard = MessageGuard(
        MessageGuard.DEFAULT_MAX_MESSAGE_AGE_MS,
        REPLAY_CAPACITY,
        MessageGuard.DEFAULT_DRIFT_TOLERANCE_MS
    )
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val topic = CryptoUtils.topicFor(pairingSecret)
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    @Volatile private var shouldStayConnected = false
    @Volatile private var reconnectAttempt = 0

    fun connect() {
        if (pairingSecret.length < 16 || socket != null) return
        shouldStayConnected = true
        val request = Request.Builder()
            .url("wss://ntfy.sh/$topic/ws")
            .build()
        socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                onConnectionChanged?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val wrapped = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (wrapped.optString("event") != "message") return
                val encrypted = wrapped.optString("message")
                if (encrypted.isBlank()) return

                scope.launch {
                    val plain = CryptoUtils.decrypt(encrypted, pairingSecret) ?: return@launch
                    val payload = MirrorPayload.fromJson(plain) ?: return@launch

                    if (!replayGuard.accept(payload.timestamp, payload.messageId)) return@launch

                    onPayload(payload)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Relay connection failed")
                handleDisconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }
        })
    }

    fun close() {
        shouldStayConnected = false
        socket?.close(1000, "closed")
        socket = null
        job.cancel()
        onConnectionChanged?.invoke(false)
    }

    private fun handleDisconnect() {
        socket = null
        onConnectionChanged?.invoke(false)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        val attempt = reconnectAttempt++
        val base = minOf(RECONNECT_BASE_MS * (1L shl minOf(attempt, RECONNECT_MAX_SHIFT)), RECONNECT_MAX_MS)
        val jitter = (base * 0.2 * (jitterRandom.nextDouble() - 0.5)).toLong()
        val delayMs = (base + jitter).coerceAtLeast(RECONNECT_BASE_MS)
        scope.launch {
            delay(delayMs)
            if (shouldStayConnected && socket == null) connect()
        }
    }

    companion object {
        private const val TAG = "CloudRelay"
        private const val REPLAY_CAPACITY = 256
        private const val RECONNECT_BASE_MS = 5_000L
        private const val RECONNECT_MAX_SHIFT = 6
        private const val RECONNECT_MAX_MS = 5 * 60 * 1000L

        private val sharedHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // Process-lifetime scope for fire-and-forget publishes; never cancelled like an app-scope.
        private val publishScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val jitterRandom = SecureRandom()

        fun publish(pairingSecret: String, payload: MirrorPayload) {
            if (pairingSecret.length < 16) return
            publishScope.launch {
                val encrypted = CryptoUtils.encrypt(payload.toJson(), pairingSecret) ?: return@launch
                try {
                    val body = encrypted.toRequestBody("text/plain; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url("https://ntfy.sh/${CryptoUtils.topicFor(pairingSecret)}")
                        .post(body)
                        .header("Cache", "no")
                        .header("Firebase", "no")
                        .build()
                    sharedHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Relay publish failed with HTTP ${response.code}")
                        }
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "Relay publish failed")
                }
            }
        }
    }
}
