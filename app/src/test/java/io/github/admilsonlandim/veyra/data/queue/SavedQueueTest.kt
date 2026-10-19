package io.github.admilsonlandim.veyra.data.queue

import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.toTrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedQueueTest {

    private fun track(id: Long, name: String = "song$id.mp3") = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = name, artist = null,
        album = null, albumId = 0, durationMs = 200_000, displayName = name, relativePath = "Music/",
        sizeBytes = 4_000_000 + id, dateModifiedSeconds = 0,
    )

    private val a = track(1)
    private val b = track(2)
    private val c = track(3)
    private val keys = listOf(a, b, c).map { it.toTrackKey() }

    @Test
    fun allTracksPresent_restoresQueueIndexAndPosition() {
        val plan = planRestore(SavedQueue(keys, currentIndex = 1, positionMs = 42_000), listOf(c, b, a))
        assertEquals(listOf(a, b, c), plan?.tracks)
        assertEquals(1, plan?.startIndex)
        assertEquals(42_000L, plan?.startPositionMs)
    }

    @Test
    fun reindexedTrack_isFoundByKey() {
        val reindexed = b.copy(id = 99, contentUri = "content://media/external/audio/media/99")
        val plan = planRestore(SavedQueue(keys, 1, 5_000), listOf(a, reindexed, c))
        assertEquals(listOf(a, reindexed, c), plan?.tracks)
        assertEquals(1, plan?.startIndex)
        assertEquals(5_000L, plan?.startPositionMs)
    }

    @Test
    fun trackBeforeCurrentGone_indexShifts() {
        val plan = planRestore(SavedQueue(keys, 2, 7_000), listOf(b, c))
        assertEquals(listOf(b, c), plan?.tracks)
        assertEquals(1, plan?.startIndex)
        assertEquals(7_000L, plan?.startPositionMs)
    }

    @Test
    fun currentTrackGone_startsNextTrackFromBeginning() {
        val plan = planRestore(SavedQueue(keys, 1, 7_000), listOf(a, c))
        assertEquals(listOf(a, c), plan?.tracks)
        assertEquals(1, plan?.startIndex)
        assertEquals(0L, plan?.startPositionMs)
    }

    @Test
    fun lastTrackGone_startsPreviousTrack() {
        val plan = planRestore(SavedQueue(keys, 2, 7_000), listOf(a, b))
        assertEquals(1, plan?.startIndex)
        assertEquals(0L, plan?.startPositionMs)
    }

    @Test
    fun nothingLeft_orNothingSaved_returnsNull() {
        assertNull(planRestore(SavedQueue(keys, 0, 0), listOf(track(50))))
        assertNull(planRestore(SavedQueue(emptyList(), 0, 0), listOf(a)))
        assertNull(planRestore(SavedQueue(keys, 0, 0), emptyList()))
    }

    @Test
    fun outOfRangeIndex_isClamped() {
        assertEquals(2, planRestore(SavedQueue(keys, 9, 1_000), listOf(a, b, c))?.startIndex)
    }

    @Test
    fun positionInClip_insideIsRelative_outsideStartsAtClipStart() {
        assertEquals(15_000L, positionInClip(45_000, clipStartMs = 30_000, clipEndMs = 120_000))
        assertEquals(0L, positionInClip(10_000, clipStartMs = 30_000, clipEndMs = 120_000))
        assertEquals(0L, positionInClip(120_000, clipStartMs = 30_000, clipEndMs = 120_000))
        assertEquals(10_000L, positionInClip(10_000, clipStartMs = 0, clipEndMs = 200_000))
    }
}
