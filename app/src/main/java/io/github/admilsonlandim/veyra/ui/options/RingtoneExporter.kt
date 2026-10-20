package io.github.admilsonlandim.veyra.ui.options

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Creates a new audio file with only a section of a track, for use as a ringtone, notification
 * or alarm sound. The original file is never modified.
 *
 * The section is re-encoded to AAC in a temporary .m4a file in the app's cache with Media3
 * Transformer, then stored through MediaStore in the matching system folder (Ringtones/,
 * Notifications/, Alarms/) and flagged as a ringtone / notification / alarm sound, so it never
 * shows up in the music library. The MediaStore entry stays pending (invisible to other apps)
 * until fully written.
 *
 * Cancelling at any point leaves nothing behind: the temporary file and a half-published entry
 * are removed even then. What a killed process leaves behind is removed by [cleanUpLeftovers].
 *
 * Android 10+ only: before that, adding a file to shared storage needs a storage write
 * permission the app does not request.
 */
class RingtoneExporter(context: Context) {

    private val appContext = context.applicationContext

    /** Returns the new file's content URI, or null if the export failed. */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun export(track: Track, clip: TrackClip, target: RingtoneTarget): Uri? {
        val temp = withContext(Dispatchers.IO) { File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, appContext.cacheDir) }
        try {
            if (!transcode(track, clip, temp)) return null
            // Publishing is short and not interruptible half-way; a cancellation is honoured
            // right after, by removing what was published.
            val uri = withContext(NonCancellable + Dispatchers.IO) { publish(temp, track, target) } ?: return null
            try {
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                delete(uri)
                throw e
            }
            return uri
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { temp.delete() }
        }
    }

    /** Removes an excerpt created by [export] (cancelled, or the system did not accept it). */
    suspend fun delete(uri: Uri) {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { appContext.contentResolver.delete(uri, null, null) }
                .onFailure { Log.w(TAG, "Couldn't remove an unused excerpt", it) }
        }
    }

    /** Transformer must be driven from one thread with a Looper: the main thread here. */
    @OptIn(UnstableApi::class)
    private suspend fun transcode(track: Track, clip: TrackClip, output: File): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(appContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            Log.w(TAG, "Excerpt export failed", exportException)
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()
                val input = MediaItem.Builder()
                    .setUri(track.contentUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs)
                            .setEndPositionMs(clip.endMs ?: track.durationMs)
                            .build(),
                    )
                    .build()
                transformer.start(EditedMediaItem.Builder(input).setRemoveVideo(true).build(), output.absolutePath)
                // Cancellation comes from the main thread (the ViewModel), where Transformer lives.
                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publish(file: File, track: Track, target: RingtoneTarget): Uri? {
        val resolver = appContext.contentResolver
        val baseName = (track.title ?: track.displayName?.substringBeforeLast('.') ?: "Veyra")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(80)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$baseName (${target.fileSuffix}).m4a")
            put(MediaStore.Audio.Media.TITLE, baseName)
            put(MediaStore.Audio.Media.MIME_TYPE, MimeTypes.AUDIO_MP4)
            put(MediaStore.Audio.Media.RELATIVE_PATH, target.folder)
            put(MediaStore.Audio.Media.IS_RINGTONE, target == RingtoneTarget.Call)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, target == RingtoneTarget.Notification)
            put(MediaStore.Audio.Media.IS_ALARM, target == RingtoneTarget.Alarm)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            // Hidden from other apps until fully written.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the excerpt", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    companion object {
        private const val TAG = "RingtoneExporter"
        private const val TEMP_PREFIX = "ringtone-export"
        private const val TEMP_SUFFIX = ".m4a"

        /** Whether an excerpt can be created on this device. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /**
         * Removes what an export interrupted by the process being killed left behind: temporary
         * files in the app's cache, and excerpt entries still pending in MediaStore. Only this
         * app's own files are considered (MediaStore's owner column, and pending entries are only
         * visible to their owner anyway), and only those created before [startedAtSeconds], so an
         * export started since is never touched. Blocking: call off the main thread.
         */
        fun cleanUpLeftovers(context: Context, startedAtSeconds: Long) {
            context.cacheDir.listFiles()
                ?.filter { isLeftoverTempFile(it.name) }
                ?.forEach { it.delete() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pendingExcerpts(context)
                    .filter { isLeftoverExcerpt(it, context.packageName, startedAtSeconds) }
                    .forEach { row ->
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, row.id)
                        context.contentResolver.delete(includingPending(uri), null, null)
                    }
            }
        }

        /** A temporary file named by [export]. */
        fun isLeftoverTempFile(name: String): Boolean = name.startsWith(TEMP_PREFIX) && name.endsWith(TEMP_SUFFIX)

        /** A pending entry this app created in one of the excerpt folders before [startedAtSeconds]. */
        fun isLeftoverExcerpt(row: PendingAudio, ownPackage: String, startedAtSeconds: Long): Boolean =
            row.ownerPackage == ownPackage &&
                row.relativePath in RingtoneTarget.entries.map { it.folder } &&
                row.dateAddedSeconds < startedAtSeconds

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun pendingExcerpts(context: Context): List<PendingAudio> {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.OWNER_PACKAGE_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
            )
            val resolver = context.contentResolver
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY) }
                resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, args, null)
            } else {
                resolver.query(
                    includingPending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
                    projection,
                    "${MediaStore.Audio.Media.IS_PENDING} = 1",
                    null,
                    null,
                )
            }
            return cursor?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(PendingAudio(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
                    }
                }
            }.orEmpty()
        }

        // Android 10 needs the URI flagged to see or delete pending entries; 11+ ignores it.
        @Suppress("DEPRECATION")
        private fun includingPending(uri: Uri): Uri =
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) MediaStore.setIncludePending(uri) else uri
    }
}

/** A pending MediaStore audio entry, as [RingtoneExporter.cleanUpLeftovers] sees it. */
data class PendingAudio(val id: Long, val relativePath: String?, val ownerPackage: String?, val dateAddedSeconds: Long)

// Environment.DIRECTORY_RINGTONES & co. hold these same standard names; they are spelled out
// because those fields are not compile-time constants (and so not usable in JVM tests).
private val RingtoneTarget.folder: String
    get() = when (this) {
        RingtoneTarget.Call -> "Ringtones/"
        RingtoneTarget.Notification -> "Notifications/"
        RingtoneTarget.Alarm -> "Alarms/"
    }

/** Kept in English: it is part of a file name, visible in other apps' sound pickers too. */
private val RingtoneTarget.fileSuffix: String
    get() = when (this) {
        RingtoneTarget.Call -> "ringtone"
        RingtoneTarget.Notification -> "notification"
        RingtoneTarget.Alarm -> "alarm"
    }
