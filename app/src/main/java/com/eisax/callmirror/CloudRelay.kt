package com.eisax.callmirror

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudRelay(
    private val pairingSecret: String,
    private val onPayload: (MirrorPayload) -> Unit,
    private val onConnectionChanged: ((Boolean) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val topic = CryptoUtils.topicFor(pairingSecret)
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    @Volatile private var shouldStayConnected = false

    fun connect() {
        if (pairingSecret.length < 16) return
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
                MirrorPayload.fromJson(plain)?.let(onPayload)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("CloudRelay", "Relay connection failed")
                onConnectionChanged?.invoke(false)
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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
                    .header("Cache-Control", "no-store")
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
        onConnectionChanged?.invoke(false)
    }

    private fun reconnect() {
        if (!shouldStayConnected) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (shouldStayConnected) connect()
        }, 5000)
    }
}
