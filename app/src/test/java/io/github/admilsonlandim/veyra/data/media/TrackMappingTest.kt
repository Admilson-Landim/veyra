package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TrackMappingTest {

    @Test
    fun cleanTag_missingValuesBecomeNull() {
        assertNull(cleanTag(null))
        assertNull(cleanTag(""))
        assertNull(cleanTag("   "))
        assertNull(cleanTag("<unknown>"))
        assertNull(cleanTag("<UNKNOWN>"))
        assertNull(cleanTag("  <unknown> "))
    }

    @Test
    fun cleanTag_keepsRealValuesTrimmed() {
        assertEquals("Nina Simone", cleanTag("  Nina Simone "))
        assertEquals("unknown", cleanTag("unknown"))
    }

    @Test
    fun resolveTitle_prefersTitleTag() {
        assertEquals("Feeling Good", resolveTitle("Feeling Good", "01 - track.mp3"))
    }

    @Test
    fun resolveTitle_fallsBackToFileNameWithoutExtension() {
        assertEquals("01 - track", resolveTitle(null, "01 - track.mp3"))
        assertEquals("01 - track", resolveTitle("<unknown>", "01 - track.mp3"))
        assertEquals("my.song", resolveTitle(" ", "my.song.flac"))
        assertEquals("noextension", resolveTitle(null, "noextension"))
    }

    @Test
    fun resolveTitle_nullWhenNothingUsable() {
        assertNull(resolveTitle(null, null))
        assertNull(resolveTitle("", ".mp3"))
    }

    @Test
    fun sortedByTitle_ignoresCaseAndAccents() {
        val tracks = listOf(
            track(1, "zebra"),
            track(2, "Água"),
            track(3, "ábaco"),
            track(4, "Banana"),
            track(5, "agua"),
            track(6, null),
            track(7, "abc"),
        )

        val sorted = tracks.sortedByTitle(Locale.ROOT).map { it.id }

        // "Água" and "agua" are equal at PRIMARY strength, so their ids decide the order.
        // Untitled tracks go last.
        assertEquals(listOf(3L, 7L, 2L, 5L, 4L, 1L, 6L), sorted)
    }

    private fun track(id: Long, title: String?) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = title, artist = null,
        album = null, albumId = 0, durationMs = 1_000, displayName = null, relativePath = null,
        sizeBytes = 0, dateModifiedSeconds = 0,
    )
}
