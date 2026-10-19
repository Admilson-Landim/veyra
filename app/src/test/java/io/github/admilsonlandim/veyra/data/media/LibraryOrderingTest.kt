package io.github.admilsonlandim.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LibraryOrderingTest {

    private fun track(
        id: Long,
        title: String? = "t$id",
        artist: String? = null,
        album: String? = null,
        duration: Long = 200_000,
        added: Long = 0,
        path: String? = "Music/",
    ) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = title, artist = artist,
        album = album, albumId = 0, durationMs = duration, displayName = "$id.mp3", relativePath = path,
        sizeBytes = 1, dateModifiedSeconds = 0, dateAddedSeconds = added,
    )

    private fun List<Track>.ids(sort: TrackSort) = sortedFor(sort, Locale.ROOT).map { it.id }

    @Test
    fun title_ignoresCaseAndAccents_unknownLast() {
        val tracks = listOf(track(1, "Zebra"), track(2, "água"), track(3, null), track(4, "Abc"))
        assertEquals(listOf(4L, 2L, 1L, 3L), tracks.ids(TrackSort.Title))
    }

    @Test
    fun artist_ignoresAccents_unknownLast_thenTitle() {
        val tracks = listOf(
            track(1, "b", artist = "Élodie"),
            track(2, "a", artist = null),
            track(3, "a", artist = "elodie"),
            track(4, "z", artist = "Ana"),
        )
        // "Élodie" == "elodie" at primary strength, so their titles decide; unknown artist last.
        assertEquals(listOf(4L, 3L, 1L, 2L), tracks.ids(TrackSort.Artist))
    }

    @Test
    fun album_unknownLast() {
        val tracks = listOf(track(1, album = null), track(2, album = "Óasis"), track(3, album = "Abba"))
        assertEquals(listOf(3L, 2L, 1L), tracks.ids(TrackSort.Album))
    }

    @Test
    fun dateAdded_isNewestFirst() {
        val tracks = listOf(track(1, added = 100), track(2, added = 300), track(3, added = 200))
        assertEquals(listOf(2L, 3L, 1L), tracks.ids(TrackSort.DateAdded))
    }

    @Test
    fun duration_isShortestFirst_tiesByTitle() {
        val tracks = listOf(track(1, "b", duration = 300), track(2, "a", duration = 300), track(3, duration = 100))
        assertEquals(listOf(3L, 2L, 1L), tracks.ids(TrackSort.Duration))
    }

    @Test
    fun folders_areNormalisedAndMatchSubfolders() {
        assertEquals("Recordings/", normalizeFolder("/Recordings"))
        assertEquals("", normalizeFolder("  /  "))
        assertTrue(isInFolder("Recordings/", "Recordings/"))
        assertTrue(isInFolder("recordings/Call/", "Recordings"))
        assertFalse(isInFolder("Music/Recordings/", "Recordings/"))
        assertFalse(isInFolder("RecordingsOld/", "Recordings/"))
        assertFalse(isInFolder(null, "Recordings/"))
        assertFalse(isInFolder("Music/", ""))
    }

    @Test
    fun filter_excludesFoldersAndShortTracks() {
        val tracks = listOf(
            track(1, path = "Music/"),
            track(2, path = "Recordings/Voice/"),
            track(3, path = "WhatsApp/Media/WhatsApp Voice Notes/202410/"),
            track(4, path = "Music/", duration = 10_000),
            track(5, path = null),
        )
        val excluded = setOf("Recordings/", "WhatsApp/Media/WhatsApp Voice Notes/")

        assertEquals(listOf(1L, 4L, 5L), tracks.filteredFor(excluded, minDurationMs = null).map { it.id })
        assertEquals(listOf(1L, 5L), tracks.filteredFor(excluded, minDurationMs = 30_000).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), tracks.filteredFor(emptySet(), null).map { it.id })
    }
}
