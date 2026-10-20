package io.github.admilsonlandim.veyra.playback

import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.toTrackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** SEC-02: the file's folder and name stay in the service; the player's items do not carry them. */
@RunWith(AndroidJUnit4::class)
class TrackKeyRegistryTest {

    private val track = Track(
        id = 42, contentUri = "content://media/external/audio/media/42", title = "Essence", artist = "Wizkid",
        album = "Made in Lagos", albumId = 1, durationMs = 250_000, displayName = "essence.mp3",
        relativePath = "Music/Private folder/", sizeBytes = 4_000_000, dateModifiedSeconds = 0,
    )

    @Test
    fun admittedItems_loseTheKey_whichStaysInTheRegistry() {
        val registry = TrackKeyRegistry()
        val item = registry.admit(listOf(track.toMediaItem(TrackClip(5_000, 60_000)))).single()

        val extras = item.mediaMetadata.extras!!
        assertEquals(setOf(EXTRA_CONTENT_URI), extras.keySet())
        assertEquals(track.contentUri, extras.getString(EXTRA_CONTENT_URI))
        assertNull(item.trackKey())
        // Everything playback needs is untouched.
        assertEquals(track.contentUri, item.localConfiguration!!.uri.toString())
        assertEquals(5_000, item.clippingConfiguration.startPositionMs)
        assertEquals("Essence", item.mediaMetadata.title)

        assertEquals(track.toTrackKey(), registry.keyOf(item))
    }

    @Test
    fun itemsWithoutAKey_passUnchanged() {
        val registry = TrackKeyRegistry()
        val plain = MediaItem.Builder().setMediaId("7").setUri("content://media/external/audio/media/7").build()
        assertEquals(plain, registry.admit(listOf(plain)).single())
        assertNull(registry.keyOf(plain))
    }
}
