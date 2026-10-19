package com.example.veyra.ui.clips

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTimeFormatTest {

    @Test
    fun formatsWithTenths() {
        assertEquals("0:00.0", formatClipTime(0))
        assertEquals("0:00.0", formatClipTime(99))
        assertEquals("0:12.3", formatClipTime(12_345))
        assertEquals("3:05.0", formatClipTime(185_000))
        assertEquals("1:00:00.5", formatClipTime(3_600_500))
        assertEquals("0:00.0", formatClipTime(-10))
    }
}
