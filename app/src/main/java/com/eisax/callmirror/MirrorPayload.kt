package com.eisax.callmirror

import org.json.JSONObject
import java.util.UUID

data class MirrorPayload(
    val type: String,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val caller: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val messageId: String = UUID.randomUUID().toString()
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("appName", appName)
        put("title", title)
        put("text", text)
        put("caller", caller)
        put("timestamp", timestamp)
        put("messageId", messageId)
    }.toString()

    companion object {
        const val TYPE_NOTIFICATION = "notification"
        const val TYPE_CALL = "call"

        fun fromJson(raw: String): MirrorPayload? {
            return try {
                val o = JSONObject(raw)
                val type = o.getString("type")
                val messageId = o.optString("messageId")
                val validType = type == TYPE_NOTIFICATION || type == TYPE_CALL
                if (validType && messageId.isNotBlank()) {
                    MirrorPayload(
                        type = type,
                        appName = o.optString("appName").takeIf { it.isNotBlank() && it != "null" },
                        title = o.optString("title").takeIf { it.isNotBlank() && it != "null" },
                        text = o.optString("text").takeIf { it.isNotBlank() && it != "null" },
                        caller = o.optString("caller").takeIf { it.isNotBlank() && it != "null" },
                        timestamp = o.optLong("timestamp", 0L),
                        messageId = messageId
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
