package io.github.admilsonlandim.veyra.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRemovalTest {

    @Test
    fun everyOccurrence_lastFirst() {
        assertEquals(listOf(3, 1), queueIndicesOf(listOf("a", "x", "b", "x"), "x"))
    }

    @Test
    fun removingInThatOrder_keepsTheOtherItems() {
        val queue = mutableListOf("x", "a", "x", "b")
        queueIndicesOf(queue, "x").forEach { queue.removeAt(it) }
        assertEquals(listOf("a", "b"), queue)
    }

    @Test
    fun absent_nothingToRemove() {
        assertEquals(emptyList<Int>(), queueIndicesOf(listOf("a"), "x"))
        assertEquals(emptyList<Int>(), queueIndicesOf(emptyList(), "x"))
    }
}
