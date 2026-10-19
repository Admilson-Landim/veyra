package com.example.veyra.playback

import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.veyra.data.clips.ClipMode
import com.example.veyra.data.clips.GlobalClipRule
import com.example.veyra.data.clips.ResolvedClip
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.clips.effectiveClip
import com.example.veyra.data.media.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented because MediaItem relies on android.net.Uri, which is only a stub on the JVM.
@RunWith(AndroidJUnit4::class)
class MediaItemMappingTest {

    private fun track(title: String? = "Feeling Good", artist: String? = "Nina Simone", album: String? = "I Put a Spell on You") =
        Track(
            id = 42, contentUri = "content://media/external/audio/media/42", title = title, artist = artist,
            album = album, albumId = 7, durationMs = 173_000, displayName = "feeling_good.mp3",
            relativePath = "Music/", sizeBytes = 1_000, dateModifiedSeconds = 0,
        )

    @Test
    fun mapsIdUriAndMetadata() {
        val item = track().toMediaItem()

        assertEquals("42", item.mediaId)
        assertEquals("content://media/external/audio/media/42", item.localConfiguration?.uri.toString())
        assertEquals("Feeling Good", item.mediaMetadata.title.toString())
        assertEquals("Nina Simone", item.mediaMetadata.artist.toString())
        assertEquals("I Put a Spell on You", item.mediaMetadata.albumTitle.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, item.mediaMetadata.mediaType)
        // Used by the UI to load the cover; travels to controllers with the item.
        assertEquals("content://media/external/audio/media/42", item.requestMetadata.mediaUri.toString())
    }

    @Test
    fun noArtworkInMediaItem_soEmbeddedCoverIsKept() {
        // Setting artwork here would make ExoPlayer drop the cover extracted from the file.
        val metadata = track().toMediaItem().mediaMetadata
        assertNull(metadata.artworkUri)
        assertNull(metadata.artworkData)
    }

    @Test
    fun unknownTagsStayEmpty_andTitleFallsBackToFileName() {
        val item = track(title = null, artist = null, album = null).toMediaItem()

        assertEquals("feeling_good.mp3", item.mediaMetadata.title.toString())
        assertNull(item.mediaMetadata.artist)
        assertNull(item.mediaMetadata.albumTitle)
    }

    @Test
    fun withoutClip_playsWholeFile() {
        val clipping = track().toMediaItem().clippingConfiguration
        assertEquals(0L, clipping.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, clipping.endPositionMs)
    }

    @Test
    fun withClip_appliesStartAndEnd() {
        val clipping = track().toMediaItem(TrackClip(30_000, 150_000)).clippingConfiguration
        assertEquals(30_000L, clipping.startPositionMs)
        assertEquals(150_000L, clipping.endPositionMs)
    }

    @Test
    fun clipWithoutEnd_playsToNaturalEnd() {
        val clipping = track().toMediaItem(TrackClip(30_000, null)).clippingConfiguration
        assertEquals(30_000L, clipping.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, clipping.endPositionMs)
    }

    @Test
    fun invalidClip_isIgnored() {
        // End beyond the 173 s duration: the file plays in full instead of failing.
        val clipping = track().toMediaItem(TrackClip(30_000, 400_000)).clippingConfiguration
        assertEquals(0L, clipping.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, clipping.endPositionMs)
    }

    @Test
    fun effectiveClip_eachCase_goesThroughTheSameConversion() {
        val t = track() // 173 s
        val global = GlobalClipRule(skipStartMs = 10_000, skipEndMs = 20_000)
        fun clippingFor(stored: ResolvedClip?) =
            t.toMediaItem(effectiveClip(stored, global, t.durationMs).clip).clippingConfiguration

        // No setting: the global rule.
        with(clippingFor(null)) {
            assertEquals(10_000L, startPositionMs)
            assertEquals(153_000L, endPositionMs)
        }
        // Custom clip wins over the global rule.
        with(clippingFor(ResolvedClip(listOf(1), TrackClip(30_000, 60_000), isValid = true))) {
            assertEquals(30_000L, startPositionMs)
            assertEquals(60_000L, endPositionMs)
        }
        // "Always whole file" ignores the global rule.
        with(clippingFor(ResolvedClip(listOf(1), TrackClip(0, null), isValid = true, mode = ClipMode.Full))) {
            assertEquals(0L, startPositionMs)
            assertEquals(C.TIME_END_OF_SOURCE, endPositionMs)
        }
    }
}
