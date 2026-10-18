package com.eisax.callmirror

import java.util.ArrayDeque

/**
 * Sliding-window rate limiter: an event is allowed only while fewer than
 * [maxEvents] events happened within the trailing [windowMs] milliseconds.
 * Purely JVM resident so it stays unit-testable.
 */
class RateLimiter(
    private val maxEvents: Int,
    private val windowMs: Long
) {
    private val eventTimes = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(nowMs: Long = System.currentTimeMillis()): Boolean {
        while (!eventTimes.isEmpty() && nowMs - eventTimes.peekFirst() > windowMs) {
            eventTimes.pollFirst()
        }
        if (eventTimes.size >= maxEvents) return false
        eventTimes.addLast(nowMs)
        return true
    }
}
