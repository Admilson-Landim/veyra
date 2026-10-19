package com.example.veyra.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun underOneMinute() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(999))
        assertEquals("0:07", formatDuration(7_000))
        assertEquals("0:59", formatDuration(59_999))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("3:45", formatDuration(225_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun oneHourAndAbove() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("12:00:05", formatDuration(43_205_000))
    }

    @Test
    fun negativeIsClampedToZero() {
        assertEquals("0:00", formatDuration(-5_000))
    }
}
