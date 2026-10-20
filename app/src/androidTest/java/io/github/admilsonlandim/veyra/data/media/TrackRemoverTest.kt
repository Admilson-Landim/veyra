package io.github.admilsonlandim.veyra.data.media

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Removes a file this test creates itself in Music/VeyraRemovalTest/ (a tone from the test
 * APK's assets): the user's own music is never touched. Before Android 11 the delete runs end to
 * end with the storage permission granted to the test, and the file itself must be gone, not only
 * its MediaStore entry. Android 11+ always asks the system, which a test cannot answer, so only
 * the request is checked.
 */
@RunWith(AndroidJUnit4::class)
class TrackRemoverTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private var created: Uri? = null

    @Before
    fun createTestFile() {
        assumeTrue("MediaStore inserts with RELATIVE_PATH need Android 10+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "veyra_removal_test.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/VeyraRemovalTest/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        created = uri
        resolver.openOutputStream(uri)!!.use { out -> instrumentation.context.assets.open("test_tone.mp3").use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }

    @After
    fun cleanUp() {
        created?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
    }

    private fun testTrack(uri: Uri) = Track(
        id = ContentUris.parseId(uri), contentUri = uri.toString(), title = "Veyra removal test", artist = null,
        album = null, albumId = 0, durationMs = 20_000, displayName = "veyra_removal_test.mp3",
        relativePath = "Music/VeyraRemovalTest/", sizeBytes = 0, dateModifiedSeconds = 0,
    )

    private fun exists(uri: Uri): Boolean = resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
        ?.use { it.count > 0 } ?: false

    @Test
    fun removesTheFile_orAsksTheSystemToTrashIt() = runTest {
        val uri = created!!
        assertTrue(exists(uri))
        @Suppress("DEPRECATION")
        val path = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)!!
            .use { it.moveToFirst(); it.getString(0) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val remover = TrackRemover(context)

        val step = remover.start(testTrack(uri))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(RemovalMode.Trash, remover.mode)
            assertTrue("expected a trash request, was $step", step is RemovalStep.NeedsSystemConfirmation)
            assertTrue("nothing is trashed before the user confirms", exists(uri))
        } else {
            assertEquals(RemovalMode.Delete, remover.mode)
            assertEquals(RemovalStep.Removed, step)
            assertTrue("the entry is gone", !exists(uri))
            assertTrue("the file itself is gone: $path", !File(path).exists())
            created = null
        }
    }
}
