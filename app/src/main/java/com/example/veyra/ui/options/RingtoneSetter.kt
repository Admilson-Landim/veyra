package com.example.veyra.ui.options

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.media.Track
import kotlinx.coroutines.Dispatchers
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
        val uri = if (excerpt != null && RingtoneExporter.isSupported) {
            RingtoneExporter(context).export(track, excerpt, target) ?: return false
        } else {
            track.contentUri.toUri()
        }
        return apply(context, target, uri)
    }

    private suspend fun apply(context: Context, target: RingtoneTarget, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, target.type, uri)
            RingtoneManager.getActualDefaultRingtoneUri(context, target.type) != null
        }.getOrDefault(false)
    }
}
