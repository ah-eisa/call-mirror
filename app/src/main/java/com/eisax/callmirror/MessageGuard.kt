package com.eisax.callmirror

import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * Freshness window plus message-id replay protection, shared by the cloud
 * relay and the local server. Purely JVM resident so it stays unit-testable.
 */
class MessageGuard(
    private val maxMessageAgeMs: Long,
    private val capacity: Int
) {
    private val seen = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > capacity
        }
    )

    /**
     * Returns true when [timestamp] sits inside the freshness window of [nowMs]
     * and [messageId] was not seen before. Eldest ids are evicted past [capacity].
     */
    fun accept(timestamp: Long, messageId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (timestamp <= 0L) return false
        if (abs(nowMs - timestamp) > maxMessageAgeMs) return false
        return seen.putIfAbsent(messageId, nowMs) == null
    }
}
