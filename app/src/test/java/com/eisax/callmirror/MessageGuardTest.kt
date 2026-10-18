package com.eisax.callmirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageGuardTest {

    private val windowMs = 10 * 60 * 1000L

    @Test
    fun acceptsFreshUnseenMessage() {
        val guard = MessageGuard(windowMs, 256)
        assertTrue(guard.accept(5_000L, "m1", nowMs = 6_000L))
    }

    @Test
    fun rejectsDuplicateMessageId() {
        val guard = MessageGuard(windowMs, 256)
        assertTrue(guard.accept(5_000L, "m1", nowMs = 6_000L))
        assertFalse(guard.accept(5_000L, "m1", nowMs = 6_000L))
    }

    @Test
    fun rejectsInvalidAndStaleTimestamps() {
        val guard = MessageGuard(windowMs, 256)
        assertFalse(guard.accept(0L, "m2", nowMs = 6_000L))
        assertFalse(guard.accept(6_000L, "m3", nowMs = 6_000L + windowMs + 1))
    }

    @Test
    fun rejectsFarFutureTimestamp() {
        val guard = MessageGuard(windowMs, 256)
        assertFalse(guard.accept(6_000L + windowMs + 1, "m4", nowMs = 6_000L))
    }

    @Test
    fun acceptsFreshTimestampAtWindowEdge() {
        val guard = MessageGuard(windowMs, 256)
        assertTrue(guard.accept(6_000L, "m5", nowMs = 6_000L + windowMs))
    }

    @Test
    fun evictsOldestBeyondCapacity() {
        val small = MessageGuard(windowMs, 1)
        assertTrue(small.accept(1_000L, "a", nowMs = 2_000L))
        assertFalse(small.accept(1_000L, "a", nowMs = 2_000L))
        assertTrue(small.accept(1_000L, "b", nowMs = 2_000L))
        assertTrue(small.accept(1_000L, "a", nowMs = 2_000L))
    }

    @Test
    fun toleratesPartnerClockAheadThenReCentres() {
        val guard = MessageGuard(windowMs, 256, driftToleranceMs = 15 * 60 * 1000L)
        val ahead = 20 * 60 * 1000L
        assertTrue(guard.accept(now + ahead, "d1", nowMs = now))
        assertTrue(guard.accept(now + ahead + 60_000L, "d2", nowMs = now + 60_000L))
    }

    @Test
    fun toleratesPartnerClockBehindThenReCentres() {
        val guard = MessageGuard(windowMs, 256, driftToleranceMs = 15 * 60 * 1000L)
        val behind = 20 * 60 * 1000L
        assertTrue(guard.accept(now - behind, "e1", nowMs = now))
        assertTrue(guard.accept(now - behind + 60_000L, "e2", nowMs = now + 60_000L))
    }

    @Test
    fun rejectsDriftBeyondBootstrapWindow() {
        val tolerance = 15 * 60 * 1000L
        val guard = MessageGuard(windowMs, 256, driftToleranceMs = tolerance)
        val bootstrap = windowMs + tolerance
        assertFalse(guard.accept(now + bootstrap + 1, "f1", nowMs = now))
        assertFalse(guard.accept(now - bootstrap - 1, "f2", nowMs = now))
    }

    @Test
    fun keepsStrictPastReplayWindowAfterLearning() {
        val guard = MessageGuard(windowMs, 256, driftToleranceMs = 15 * 60 * 1000L)
        assertTrue(guard.accept(now, "g1", nowMs = now))
        assertFalse(guard.accept(now - windowMs - 1, "g2", nowMs = now))
    }

    private companion object {
        const val now = 1_700_000_000_000L
    }
}
