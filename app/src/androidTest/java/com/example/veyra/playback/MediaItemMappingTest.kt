package com.example.veyra.playback

import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun noClippingUntilPhase7() {
        val clipping = track().toMediaItem().clippingConfiguration
        assertEquals(0L, clipping.startPositionMs)
        assertEquals(Long.MIN_VALUE, clipping.endPositionMs)
    }
}
