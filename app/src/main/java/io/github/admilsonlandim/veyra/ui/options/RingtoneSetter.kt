package io.github.admilsonlandim.veyra.ui.options

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Which system sound a track becomes. */
enum class RingtoneTarget(val type: Int) {
    Call(RingtoneManager.TYPE_RINGTONE),
    Notification(RingtoneManager.TYPE_NOTIFICATION),
    Alarm(RingtoneManager.TYPE_ALARM),
}

/**
 * Sets a track as the device ringtone, notification or alarm sound.
 *
 * Needs the special WRITE_SETTINGS access, which the user grants in system settings
 * ("Modify system settings"); there is no runtime dialog for it.
 *
 * The system always plays a whole file, so a section of a song is first exported to a new file
 * (the playback start/end clip is independent of this). On Android 10+ RingtoneManager also
 * copies the sound into the system's ringtone cache, so the call does file IO on Dispatchers.IO.
 */
object RingtoneSetter {

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Opens the "Modify system settings" page for this app. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Sets [track] as the [target] sound: the whole file when [excerpt] is null, otherwise a new
     * file holding only that section (Android 10+, see [RingtoneExporter]).
     * Returns true when the system reports the new sound afterwards.
     */
    suspend fun set(context: Context, track: Track, target: RingtoneTarget, excerpt: TrackClip? = null): Boolean {
        if (excerpt == null || !RingtoneExporter.isSupported) return apply(context, target, track.contentUri.toUri())
        val exporter = RingtoneExporter(context)
        val uri = exporter.export(track, excerpt, target) ?: return false
        // An excerpt that does not end up as the system sound (cancelled before, or refused by
        // the system) is of no use: remove it rather than leave it in the sound pickers.
        val applied = try {
            currentCoroutineContext().ensureActive()
            apply(context, target, uri)
        } catch (e: CancellationException) {
            exporter.delete(uri)
            throw e
        }
        if (!applied) exporter.delete(uri)
        return applied
    }

    /**
     * The system sounds currently set to [track]'s own file (not to an excerpt made from it), so
     * the user can be warned before it is removed. Reading them needs no permission.
     */
    suspend fun targetsUsing(context: Context, track: Track): Set<RingtoneTarget> = withContext(Dispatchers.IO) {
        RingtoneTarget.entries.filter { target ->
            runCatching {
                RingtoneManager.getActualDefaultRingtoneUri(context, target.type)?.let { isSameFile(it, track) } == true
            }.getOrDefault(false)
        }.toSet()
    }

    /** content://media/external/audio/media/<id>, possibly with a query, for the track's id. */
    private fun isSameFile(uri: Uri, track: Track): Boolean =
        uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.firstOrNull()?.startsWith("external") == true &&
            uri.lastPathSegment?.toLongOrNull() == track.id

    // Not cancellable: once started, the system sound is changed and the result must be reported.
    private suspend fun apply(context: Context, target: RingtoneTarget, uri: Uri): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, target.type, uri)
            RingtoneManager.getActualDefaultRingtoneUri(context, target.type) != null
        }.getOrDefault(false)
    }
}
