package com.eisax.callmirror

import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * Freshness window plus message-id replay protection, shared by the cloud
 * relay and the local server. Purely JVM resident so it stays unit-testable.
 *
 * Clock drift: both devices stamp messages with their own wall clock, so a
 * partner whose clock is off would otherwise have every message rejected.
 * The guard therefore learns the partner's offset from accepted (authenticated)
 * messages and centres the freshness window on it:
 *
 *  - before any offset is known the window is symmetric and as wide as
 *    [maxMessageAgeMs] + [driftToleranceMs], so the first exchange survives
 *    drift in either direction;
 *  - afterwards the past edge stays a strict [maxMessageAgeMs] replay window
 *    around the learned offset, while the future edge allows [driftToleranceMs].
 *
 * Setting [driftToleranceMs] to 0 keeps the historic symmetric behaviour.
 */
class MessageGuard(
    private val maxMessageAgeMs: Long,
    private val capacity: Int,
    private val driftToleranceMs: Long = 0L
) {
    private val seen = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > capacity
        }
    )

    private var partnerOffsetMs = 0L
    private var offsetLearned = false

    /**
     * Returns true when [timestamp] sits inside the freshness window of [nowMs]
     * and [messageId] was not seen before. Eldest ids are evicted past [capacity].
     */
    @Synchronized
    fun accept(timestamp: Long, messageId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (timestamp <= 0L) return false

        val skew = timestamp - nowMs
        val withinWindow = if (offsetLearned) {
            skew >= partnerOffsetMs - maxMessageAgeMs && skew <= partnerOffsetMs + driftToleranceMs
        } else {
            val bootstrap = maxMessageAgeMs + driftToleranceMs
            abs(skew) <= bootstrap
        }
        if (!withinWindow) return false
        if (seen.putIfAbsent(messageId, nowMs) != null) return false

        partnerOffsetMs = skew.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        offsetLearned = true
        return true
    }

    companion object {
        const val DEFAULT_MAX_MESSAGE_AGE_MS = 10 * 60 * 1000L
        const val DEFAULT_DRIFT_TOLERANCE_MS = 15 * 60 * 1000L

        // A learned offset is only trusted within sane bounds.
        private const val MAX_OFFSET_MS = 24 * 60 * 60 * 1000L
    }
}
