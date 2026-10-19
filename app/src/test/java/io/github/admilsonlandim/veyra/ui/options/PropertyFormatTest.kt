package io.github.admilsonlandim.veyra.ui.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class PropertyFormatTest {

    private val pt = Locale.forLanguageTag("pt-PT")

    @Test
    fun bitrate() {
        assertEquals("320 kbps", formatBitrate(320_000))
        assertEquals("128 kbps", formatBitrate(127_600))
        assertNull(formatBitrate(null))
        assertNull(formatBitrate(0))
    }

    @Test
    fun sampleRate_usesLocaleDecimalSeparator() {
        assertEquals("44,1 kHz", formatSampleRate(44_100, pt))
        assertEquals("48 kHz", formatSampleRate(48_000, pt))
        assertEquals("44.1 kHz", formatSampleRate(44_100, Locale.US))
        assertNull(formatSampleRate(null, pt))
    }

    @Test
    fun fileSize_decimalUnits() {
        assertEquals("4,3 MB", formatFileSize(4_256_684, pt))
        assertEquals("999 B", formatFileSize(999, pt))
        assertEquals("1,5 KB", formatFileSize(1_500, pt))
        assertEquals("2,1 GB", formatFileSize(2_100_000_000, pt))
        assertNull(formatFileSize(0, pt))
    }

    @Test
    fun trackNumber_stripsLeadingZeros() {
        assertEquals("3/12", formatTrackNumber("03/12"))
        assertEquals("7", formatTrackNumber(" 7 "))
        assertEquals("A1", formatTrackNumber("A1"))
        assertNull(formatTrackNumber(""))
        assertNull(formatTrackNumber(null))
    }

    @Test
    fun year_extractedFromDates() {
        assertEquals("2019", formatYear("2019-05-02T00:00:00"))
        assertEquals("1987", formatYear("1987"))
        assertEquals("abc", formatYear("abc"))
        assertNull(formatYear(null))
    }

    @Test
    fun mimeType_friendlyNames() {
        assertEquals("MP3 (audio/mpeg)", formatMimeType("audio/mpeg"))
        assertEquals("FLAC (audio/flac)", formatMimeType("AUDIO/FLAC"))
        assertEquals("audio/x-unknown", formatMimeType("audio/x-unknown"))
        assertNull(formatMimeType(" "))
    }

    @Test
    fun dateAdded_inGivenTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val formatted = formatDateAdded(1_700_000_000, Locale.US, utc)
        // 2023-11-14 22:13 UTC; exact punctuation depends on the JDK's CLDR data.
        assertEquals(true, formatted!!.contains("2023") && formatted.contains("Nov") && formatted.contains("10:13"))
        assertNull(formatDateAdded(0))
    }
}
