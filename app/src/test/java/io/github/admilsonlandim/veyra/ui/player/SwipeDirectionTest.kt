package io.github.admilsonlandim.veyra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeDirectionTest {

    private val width = 1000f
    private val fling = 2000f

    private fun decide(distance: Float, velocity: Float) = swipeDirection(distance, velocity, width, fling)

    @Test
    fun farEnough_countsWithoutVelocity() {
        assertEquals(-1, decide(-301f, 0f)) // left: next
        assertEquals(1, decide(301f, 0f)) // right: previous
    }

    @Test
    fun shortSlowDrag_springsBack() {
        assertEquals(0, decide(-200f, -500f))
        assertEquals(0, decide(0f, 0f))
    }

    @Test
    fun fastFlingInTheDragDirection_counts() {
        assertEquals(-1, decide(-80f, -2500f))
        assertEquals(1, decide(80f, 2500f))
    }

    @Test
    fun flingAgainstTheDrag_springsBack() {
        // Dragged left a bit, then flicked back to the right: not a "previous".
        assertEquals(0, decide(-80f, 2500f))
    }
}
