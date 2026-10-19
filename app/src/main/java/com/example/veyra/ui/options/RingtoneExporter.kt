package com.example.veyra.ui.options

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.media.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Creates a new audio file with only a section of a track, for use as a ringtone, notification
 * or alarm sound. The original file is never modified.
 *
 * The section is re-encoded to AAC in an .m4a file with Media3 Transformer, then stored through
 * MediaStore in the matching system folder (Ringtones/, Notifications/, Alarms/) and flagged as a
 * ringtone / notification / alarm sound, so it never shows up in the music library.
 *
 * Android 10+ only: before that, adding a file to shared storage needs a storage write
 * permission the app does not request.
 */
class RingtoneExporter(context: Context) {

    private val appContext = context.applicationContext

    /** Returns the new file's content URI, or null if the export failed. */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun export(track: Track, clip: TrackClip, target: RingtoneTarget): Uri? {
        val temp = File(appContext.cacheDir, "ringtone-export.m4a")
        return try {
            if (!transcode(track, clip, temp)) return null
            withContext(Dispatchers.IO) { publish(temp, track, target) }
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
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

        /** Whether an excerpt can be created on this device. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

private val RingtoneTarget.folder: String
    get() = when (this) {
        RingtoneTarget.Call -> Environment.DIRECTORY_RINGTONES
        RingtoneTarget.Notification -> Environment.DIRECTORY_NOTIFICATIONS
        RingtoneTarget.Alarm -> Environment.DIRECTORY_ALARMS
    } + "/"

/** Kept in English: it is part of a file name, visible in other apps' sound pickers too. */
private val RingtoneTarget.fileSuffix: String
    get() = when (this) {
        RingtoneTarget.Call -> "ringtone"
        RingtoneTarget.Notification -> "notification"
        RingtoneTarget.Alarm -> "alarm"
    }
