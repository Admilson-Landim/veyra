package com.example.veyra.ui.options

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.net.toUri
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
 * The whole file is used: the per-track start/end clip does not apply to system sounds.
 * On Android 10+ RingtoneManager also copies the sound into the system's ringtone cache, so the
 * call does file IO and runs on Dispatchers.IO.
 */
object RingtoneSetter {

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Opens the "Modify system settings" page for this app. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Returns true when the system reports the new sound afterwards. */
    suspend fun set(context: Context, track: Track, target: RingtoneTarget): Boolean = withContext(Dispatchers.IO) {
        val uri = track.contentUri.toUri()
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, target.type, uri)
            RingtoneManager.getActualDefaultRingtoneUri(context, target.type) != null
        }.getOrDefault(false)
    }
}
