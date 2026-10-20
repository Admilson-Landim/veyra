package io.github.admilsonlandim.veyra.data.media

import io.github.admilsonlandim.veyra.data.queue.SavedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackReferencesTest {

    private fun track(id: Long, name: String = "song$id.mp3", path: String? = "Music/", size: Long = 1_000 + id) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = "t$id", artist = null, album = null,
        albumId = 0, durationMs = 180_000, displayName = name, relativePath = path, sizeBytes = size, dateModifiedSeconds = 0,
    )

    private val removed = track(7)

    @Test
    fun refersTo_sameIdAndFile_orSameLocation() {
        assertTrue(removed.toTrackKey().refersTo(removed))
        // Re-indexed: new id, same folder and name.
        assertTrue(removed.toTrackKey().copy(mediaStoreId = 99).refersTo(removed))
        // Re-tagged: same id and name, new size.
        assertTrue(removed.toTrackKey().copy(sizeBytes = 5).refersTo(removed))
    }

    @Test
    fun refersTo_notAnotherFile() {
        assertFalse(track(8).toTrackKey().refersTo(removed))
        // The id was reused by MediaStore for a different file.
        assertFalse(track(7, name = "other.mp3", size = 1).toTrackKey().copy(relativePath = "Other/").refersTo(removed))
        // Same name in another folder.
        assertFalse(track(8, name = "song7.mp3", path = "Other/").toTrackKey().refersTo(removed))
    }

    private fun queue(vararg ids: Long, current: Int, position: Long = 42_000) =
        SavedQueue(ids.map { if (it == 7L) removed.toTrackKey() else track(it).toTrackKey() }, current, position)

    @Test
    fun savedQueue_withoutTheTrack_keepsTheCurrentItem() {
        val result = queue(1, 7, 2, 3, current = 2).without(removed)
        assertEquals(listOf(1L, 2L, 3L), result.keys.map { it.mediaStoreId })
        assertEquals(1, result.currentIndex)
        assertEquals(42_000, result.positionMs)
    }

    @Test
    fun savedQueue_removingTheCurrent_movesToTheNextFromItsStart() {
        val result = queue(1, 7, 2, current = 1).without(removed)
        assertEquals(listOf(1L, 2L), result.keys.map { it.mediaStoreId })
        assertEquals(1, result.currentIndex)
        assertEquals(0, result.positionMs)
    }

    @Test
    fun savedQueue_removingTheLastCurrent_staysOnTheNewLast() {
        val result = queue(1, 2, 7, current = 2).without(removed)
        assertEquals(1, result.currentIndex)
        assertEquals(0, result.positionMs)
    }

    @Test
    fun savedQueue_everyOccurrence_andEmptyWhenOnlyThatTrack() {
        assertEquals(listOf(1L), queue(7, 1, 7, current = 1).without(removed).keys.map { it.mediaStoreId })
        val empty = queue(7, 7, current = 0).without(removed)
        assertEquals(emptyList<TrackKey>(), empty.keys)
        assertEquals(0, empty.currentIndex)
    }

    @Test
    fun savedQueue_withoutTheTrack_isUnchanged() {
        val saved = queue(1, 2, current = 0)
        assertSame(saved, saved.without(removed))
    }
}
