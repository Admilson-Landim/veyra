package io.github.admilsonlandim.veyra.data.clips

import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.toTrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackClipTest {

    private val duration = 200_000L

    @Test
    fun valid_clips() {
        assertEquals(ClipValidation.Valid, validateClip(0, null, duration))
        assertEquals(ClipValidation.Valid, validateClip(30_000, 170_000, duration))
        assertEquals(ClipValidation.Valid, validateClip(0, duration, duration)) // end exactly at duration
        assertEquals(ClipValidation.Valid, validateClip(duration - MIN_CLIP_LENGTH_MS, null, duration))
    }

    @Test
    fun startEqualToEnd_isRejected() {
        assertEquals(ClipValidation.StartNotBeforeEnd, validateClip(50_000, 50_000, duration))
        assertEquals(ClipValidation.StartNotBeforeEnd, validateClip(60_000, 50_000, duration))
        // No explicit end: the end is the duration.
        assertEquals(ClipValidation.StartNotBeforeEnd, validateClip(duration, null, duration))
    }

    @Test
    fun shorterThanMinimum_isRejected() {
        assertEquals(ClipValidation.TooShort, validateClip(50_000, 50_000 + MIN_CLIP_LENGTH_MS - 1, duration))
        assertEquals(ClipValidation.Valid, validateClip(50_000, 50_000 + MIN_CLIP_LENGTH_MS, duration))
    }

    @Test
    fun endBeyondDuration_isRejected() {
        assertEquals(ClipValidation.EndBeyondDuration, validateClip(0, duration + 1, duration))
    }

    @Test
    fun negativeValues_areRejected() {
        assertEquals(ClipValidation.NegativeStart, validateClip(-1, 10_000, duration))
        assertEquals(ClipValidation.StartNotBeforeEnd, validateClip(0, -5_000, duration))
    }

    @Test
    fun unknownDuration_isRejected() {
        assertEquals(ClipValidation.UnknownDuration, validateClip(0, 10_000, 0))
    }

    @Test
    fun durationChanged_previouslyValidClipBecomesInvalid() {
        val clip = TrackClip(30_000, 190_000)
        assertEquals(ClipValidation.Valid, clip.validateFor(200_000))
        // The file was replaced by a shorter one.
        assertEquals(ClipValidation.EndBeyondDuration, clip.validateFor(150_000))
        assertEquals(ClipValidation.StartNotBeforeEnd, TrackClip(160_000, null).validateFor(150_000))
    }

    @Test
    fun clipToApply_withAndWithoutConfiguration() {
        assertNull(clipToApply(null, duration))
        assertNull(clipToApply(TrackClip(0, null), duration)) // default: nothing to apply
        assertEquals(TrackClip(30_000, 170_000), clipToApply(TrackClip(30_000, 170_000), duration))
        assertEquals(TrackClip(30_000, null), clipToApply(TrackClip(30_000, null), duration))
    }

    @Test
    fun clipToApply_invalidConfigurationIsIgnored() {
        assertNull(clipToApply(TrackClip(30_000, 250_000), duration))
        assertNull(clipToApply(TrackClip(100_000, 100_500), duration))
    }

    private fun track(id: Long, name: String, durationMs: Long = duration) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = name, artist = null,
        album = null, albumId = 0, durationMs = durationMs, displayName = "$name.mp3", relativePath = "Music/",
        sizeBytes = 1_000 + id, dateModifiedSeconds = 0,
    )

    @Test
    fun resolveClips_matchesByTrackKey_andFlagsInvalid() {
        val a = track(1, "a")
        val b = track(2, "b")
        val stored = listOf(
            StoredClip(rowId = 10, key = a.toTrackKey(), clip = TrackClip(5_000, 60_000), updatedAt = 1),
            StoredClip(rowId = 11, key = b.toTrackKey(), clip = TrackClip(5_000, 60_000), updatedAt = 1),
        )
        // "b" was replaced by a 30 s file with the same name: its clip no longer fits.
        val library = listOf(a, track(2, "b", durationMs = 30_000).copy(sizeBytes = b.sizeBytes))

        val resolved = resolveClips(stored, library)

        assertTrue(resolved.getValue(1).isValid)
        assertFalse(resolved.getValue(2).isValid)
    }

    @Test
    fun resolveClips_followsAReindexedTrack_andLatestRowWins() {
        val original = track(1, "song")
        val reindexed = original.copy(id = 99)
        val stored = listOf(
            StoredClip(rowId = 10, key = original.toTrackKey(), clip = TrackClip(1_000, null), updatedAt = 1),
            StoredClip(rowId = 11, key = reindexed.toTrackKey(), clip = TrackClip(9_000, null), updatedAt = 2),
        )

        val resolved = resolveClips(stored, listOf(reindexed)).getValue(99)

        assertEquals(TrackClip(9_000, null), resolved.clip)
        assertEquals(setOf(10L, 11L), resolved.rowIds.toSet())
    }

    @Test
    fun resolveClips_unknownTracksAreDropped() {
        val stored = listOf(StoredClip(1, track(5, "gone").toTrackKey(), TrackClip(1_000, null), 1))
        assertTrue(resolveClips(stored, listOf(track(6, "other"))).isEmpty())
    }
}
