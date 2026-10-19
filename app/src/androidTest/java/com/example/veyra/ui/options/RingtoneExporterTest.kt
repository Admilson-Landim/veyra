package com.example.veyra.ui.options

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.media.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class RingtoneExporterTest {

    /**
     * A 20 s, 440 Hz tone shipped in the test APK (androidTest/assets), copied to the app's cache
     * so the app can read it: no user files are involved.
     */
    private fun testTone(): Track {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "test_tone.mp3")
        instrumentation.context.assets.open("test_tone.mp3").use { input -> file.outputStream().use(input::copyTo) }
        return Track(
            id = 1, contentUri = Uri.fromFile(file).toString(), title = "Veyra test tone", artist = null,
            album = null, albumId = 0, durationMs = 20_000, displayName = "test_tone.mp3", relativePath = null,
            sizeBytes = file.length(), dateModifiedSeconds = 0,
        )
    }

    @Test
    fun exportsOnlyTheChosenSection_asANotificationSound() = runTest(timeout = 60.seconds) {
        assumeTrue("Excerpts need Android 10+", RingtoneExporter.isSupported)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val tone = testTone()

        val uri = RingtoneExporter(context).export(tone, TrackClip(5_000, 13_000), RingtoneTarget.Notification)
        assertNotNull("export failed", uri)
        try {
            // About 8 s long (AAC frames add a few tens of milliseconds at most).
            val durationMs = MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri!!)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                } finally {
                    release()
                }
            }
            assertTrue("duration was $durationMs ms", durationMs in 7_700L..8_300L)

            // Stored as a notification sound, not as music, in Notifications/.
            resolver.query(
                uri!!,
                arrayOf(
                    MediaStore.Audio.Media.IS_NOTIFICATION,
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )!!.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(0, c.getInt(1))
                assertEquals("Notifications/", c.getString(2))
                // MediaStore's MIME_TYPE for .m4a varies by Android version (audio/mp4 or, on 10,
                // audio/mpeg); the AAC/MP4 content is what matters and was decoded above.
                assertEquals("Veyra test tone (notification).m4a", c.getString(3))
            }
        } finally {
            uri?.let { resolver.delete(it, null, null) }
        }
    }
}
