package com.example.veyra.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackKeyResolverTest {

    private fun track(
        id: Long,
        name: String? = "song.mp3",
        path: String? = "Music/",
        size: Long = 4_000_000,
        duration: Long = 200_000,
    ) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = name, artist = null,
        album = null, albumId = 0, durationMs = duration, displayName = name, relativePath = path,
        sizeBytes = size, dateModifiedSeconds = 0,
    )

    private val original = track(id = 10)
    private val key = original.toTrackKey()

    @Test
    fun sameId_sameFile_matches() {
        assertEquals(10L, TrackKeyResolver(listOf(original, track(11, "other.mp3"))).resolve(key)?.id)
    }

    @Test
    fun sameId_retaggedInPlace_stillMatches() {
        // Tag edit changes the size but not the name.
        val retagged = track(id = 10, size = 4_100_000)
        assertEquals(10L, TrackKeyResolver(listOf(retagged)).resolve(key)?.id)
    }

    @Test
    fun idReusedForADifferentFile_isRejected() {
        val impostor = track(id = 10, name = "other.mp3", size = 9_999)
        assertNull(TrackKeyResolver(listOf(impostor)).resolve(key))
    }

    @Test
    fun reindexed_newId_matchesByPathAndName() {
        val reindexed = track(id = 99)
        assertEquals(99L, TrackKeyResolver(listOf(track(5, "a.mp3"), reindexed)).resolve(key)?.id)
    }

    @Test
    fun moved_newIdAndFolder_matchesByNameAndSize() {
        val moved = track(id = 50, path = "Download/")
        assertEquals(50L, TrackKeyResolver(listOf(moved)).resolve(key)?.id)
    }

    @Test
    fun movedAndRetagged_matchesByNameAndDuration() {
        val moved = track(id = 51, path = "Download/", size = 4_200_000, duration = 201_000)
        assertEquals(51L, TrackKeyResolver(listOf(moved)).resolve(key)?.id)
    }

    @Test
    fun durationOutsideTolerance_isNotAMatch() {
        val different = track(id = 52, path = "Download/", size = 1, duration = 260_000)
        assertNull(TrackKeyResolver(listOf(different)).resolve(key))
    }

    @Test
    fun ambiguousCopies_inOtherFolders_resolveToUnavailable() {
        val copyA = track(id = 60, path = "Download/")
        val copyB = track(id = 61, path = "Backup/")
        assertNull(TrackKeyResolver(listOf(copyA, copyB)).resolve(key))
    }

    @Test
    fun copiesInSeveralFolders_areToldApartByPath() {
        val reindexedOriginal = track(id = 70)
        val copyElsewhere = track(id = 71, path = "Backup/")
        assertEquals(70L, TrackKeyResolver(listOf(copyElsewhere, reindexedOriginal)).resolve(key)?.id)
    }

    @Test
    fun beforeAndroid10_withoutPath_stillMatchesByNameAndSize() {
        val oldKey = track(id = 10, path = null).toTrackKey()
        val reindexed = track(id = 80, path = null)
        assertEquals(80L, TrackKeyResolver(listOf(reindexed)).resolve(oldKey)?.id)
    }

    @Test
    fun deletedFile_isUnavailable() {
        assertNull(TrackKeyResolver(listOf(track(1, "a.mp3"), track(2, "b.mp3"))).resolve(key))
        assertNull(TrackKeyResolver(emptyList()).resolve(key))
    }
}
