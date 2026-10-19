package com.example.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkDecodingTest {

    @Test
    fun inSampleSize_smallerOrEqualImage_isNotSubsampled() {
        assertEquals(1, calculateInSampleSize(192, 192, 192))
        assertEquals(1, calculateInSampleSize(100, 100, 192))
        assertEquals(1, calculateInSampleSize(383, 383, 192))
    }

    @Test
    fun inSampleSize_isLargestPowerOfTwoKeepingRequestedSize() {
        assertEquals(2, calculateInSampleSize(384, 384, 192))
        assertEquals(8, calculateInSampleSize(3000, 3000, 192))
        assertEquals(2, calculateInSampleSize(3000, 3000, 1024))
        assertEquals(4, calculateInSampleSize(4096, 4096, 1024))
    }

    @Test
    fun inSampleSize_isLimitedByTheShorterSide() {
        // 4000x500 for 192: 500/2 = 250 >= 192 but 500/4 = 125 < 192.
        assertEquals(2, calculateInSampleSize(4000, 500, 192))
    }

    @Test
    fun inSampleSize_invalidInputFallsBackToOne() {
        assertEquals(1, calculateInSampleSize(0, 1000, 192))
        assertEquals(1, calculateInSampleSize(-1, -1, 192))
        assertEquals(1, calculateInSampleSize(1000, 1000, 0))
    }

    @Test
    fun firstAvailable_returnsFirstNonNull_withoutCallingLaterSources() {
        val calls = mutableListOf<String>()
        val result = firstAvailable(
            { calls += "thumbnail"; "thumb" },
            { calls += "embedded"; "embedded" },
        )
        assertEquals("thumb", result)
        assertEquals(listOf("thumbnail"), calls)
    }

    @Test
    fun firstAvailable_fallsBackWhenASourceIsEmptyOrThrows() {
        assertEquals("embedded", firstAvailable({ null }, { "embedded" }))
        assertEquals(
            "embedded",
            firstAvailable({ throw java.io.FileNotFoundException("no thumbnail") }, { "embedded" }),
        )
    }

    @Test
    fun firstAvailable_nullWhenNoSourceHasArtwork() {
        assertNull(firstAvailable<String>({ null }, { throw IllegalStateException("corrupt") }))
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun firstAvailable_doesNotSwallowCancellation() {
        firstAvailable({ throw kotlinx.coroutines.CancellationException("scrolled away") }, { "embedded" })
    }
}
