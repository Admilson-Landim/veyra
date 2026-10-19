package com.example.veyra.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorSkipPolicyTest {

    @Test
    fun skipsWhileThereIsANextItem() {
        val policy = ErrorSkipPolicy()
        assertTrue(policy.shouldSkip(queueSize = 5, hasNext = true))
    }

    @Test
    fun stopsAtTheEndOfTheQueue() {
        val policy = ErrorSkipPolicy()
        assertFalse(policy.shouldSkip(queueSize = 5, hasNext = false))
    }

    @Test
    fun allItemsBrokenWithRepeatAll_triesEachOnceThenStops() {
        // With repeat-all, hasNext is always true: only the attempt count can stop the loop.
        val policy = ErrorSkipPolicy()
        val decisions = List(4) { policy.shouldSkip(queueSize = 3, hasNext = true) }
        assertEquals(listOf(true, true, false, false), decisions)
    }

    @Test
    fun singleBrokenItemWithRepeatAll_doesNotLoop() {
        val policy = ErrorSkipPolicy()
        assertFalse(policy.shouldSkip(queueSize = 1, hasNext = true))
    }

    @Test
    fun resetAfterSuccessfulPlayback_allowsSkippingAgain() {
        val policy = ErrorSkipPolicy()
        policy.shouldSkip(queueSize = 2, hasNext = true)
        policy.reset()
        assertTrue(policy.shouldSkip(queueSize = 2, hasNext = true))
    }
}
