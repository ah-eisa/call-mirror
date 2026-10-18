package com.eisax.callmirror

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CloudRelay(
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit,
    private val onConnectionChanged: ((Boolean) -> Unit)? = null
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val topic = CryptoUtils.topicFor(pairingSecret)
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val httpClient = sharedHttpClient

    private var socket: WebSocket? = null
    @Volatile private var shouldStayConnected = false

    private val recentMessageIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 256
        }
    )

    fun connect() {
        if (pairingSecret.length < 16 || socket != null) return
        shouldStayConnected = true
        val request = Request.Builder()
            .url("wss://ntfy.sh/$topic/ws")
            .build()
        socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionChanged?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val wrapped = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (wrapped.optString("event") != "message") return
                val encrypted = wrapped.optString("message")
                if (encrypted.isBlank()) return
                val plain = CryptoUtils.decrypt(encrypted, pairingSecret) ?: return
                val payload = MirrorPayload.fromJson(plain) ?: return

                val now = System.currentTimeMillis()
                if (abs(now - payload.timestamp) > MAX_MESSAGE_AGE_MS) return
                if (recentMessageIds.put(payload.messageId, true) != null) return

                onPayload(payload)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                Log.w("CloudRelay", "Relay connection failed")
                onConnectionChanged?.invoke(false)
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onConnectionChanged?.invoke(false)
                reconnect()
            }
        })
    }

    fun send(payload: MirrorPayload) {
        if (pairingSecret.length < 16) return
        val encrypted = CryptoUtils.encrypt(payload.toJson(), pairingSecret) ?: return
        scope.launch {
            try {
                val body = encrypted.toRequestBody("text/plain; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/$topic")
                    .post(body)
                    .header("Cache", "no")
                    .header("Firebase", "no")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CloudRelay", "Relay publish failed with HTTP ${response.code}")
                    }
                }
            } catch (_: Exception) {
                Log.w("CloudRelay", "Relay publish failed")
            }
        }
    }

    fun close() {
        shouldStayConnected = false
        socket?.close(1000, "closed")
        socket = null
        job.cancel()
        onConnectionChanged?.invoke(false)
    }

    private fun reconnect() {
        if (!shouldStayConnected) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (shouldStayConnected && socket == null) connect()
        }, 5000)
    }

    companion object {
        private const val MAX_MESSAGE_AGE_MS = 10 * 60 * 1000L
        private val sharedHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
