package com.eisax.callmirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun allowsUpToMaxEventsInsideWindow() {
        val limiter = RateLimiter(maxEvents = 3, windowMs = 1_000)
        assertTrue(limiter.tryAcquire(0L))
        assertTrue(limiter.tryAcquire(100L))
        assertTrue(limiter.tryAcquire(200L))
        assertFalse(limiter.tryAcquire(300L))
    }

    @Test
    fun throttlesEvenAcrossDifferentTimes() {
        val limiter = RateLimiter(maxEvents = 2, windowMs = 5_000)
        assertTrue(limiter.tryAcquire(0L))
        assertTrue(limiter.tryAcquire(4_999L))
        assertFalse(limiter.tryAcquire(5_000L))
    }

    @Test
    fun windowSlidesAndFreesCapacity() {
        val limiter = RateLimiter(maxEvents = 2, windowMs = 1_000)
        assertTrue(limiter.tryAcquire(0L))
        assertTrue(limiter.tryAcquire(100L))
        assertFalse(limiter.tryAcquire(500L))
        // t=0 has now aged past the window, freeing capacity
        assertTrue(limiter.tryAcquire(1_001L))
        assertTrue(limiter.tryAcquire(1_101L))
        assertFalse(limiter.tryAcquire(2_000L))
    }

    @Test
    fun reportedAsAvailableAgainAfterFullWindowElapses() {
        val limiter = RateLimiter(maxEvents = 1, windowMs = 1_000)
        assertTrue(limiter.tryAcquire(10_000L))
        assertFalse(limiter.tryAcquire(10_500L))
        assertTrue(limiter.tryAcquire(11_001L))
    }
}
