package io.github.admilsonlandim.veyra.data.media

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How a track leaves the device. */
enum class RemovalMode {
    /** Android 11+: the system trash, recoverable for about 30 days. */
    Trash,

    /** Before Android 11 there is no trash: the file is deleted for good. */
    Delete,
}

fun removalModeFor(sdkInt: Int): RemovalMode =
    if (sdkInt >= Build.VERSION_CODES.R) RemovalMode.Trash else RemovalMode.Delete

/** Where a removal stands after each step. */
sealed interface RemovalStep {
    data object Removed : RemovalStep

    /** The system must confirm (trash request, or access to another app's file on Android 10). */
    data class NeedsSystemConfirmation(val intentSender: IntentSender) : RemovalStep

    /** Android 10 and older: the storage write permission must be granted first. */
    data object NeedsWritePermission : RemovalStep

    data object Failed : RemovalStep
}

/**
 * Removes a track's file from the device through MediaStore, which also drops it from the
 * library (the repository re-queries on the change).
 *
 * - Android 11+: [MediaStore.createTrashRequest]. The system asks the user and moves the file
 *   to its trash; nothing else to do afterwards.
 * - Android 10 and older: a direct delete with WRITE_EXTERNAL_STORAGE, under legacy storage on
 *   Android 10 (see the manifest: there, the per-file system grant removes the library entry
 *   but not the file). Should MediaStore still throw [RecoverableSecurityException], the system
 *   dialog is shown and the delete is tried again in [onSystemConfirmed]. Afterwards the file
 *   itself is checked: an entry removed while the file stays is reported as a failure.
 *
 * Call [start] after the user confirmed in the app, then follow the returned step.
 */
class TrackRemover(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    val mode: RemovalMode = removalModeFor(Build.VERSION.SDK_INT)

    suspend fun start(track: Track): RemovalStep = withContext(Dispatchers.IO) {
        val uri = track.contentUri.toUri()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching {
                RemovalStep.NeedsSystemConfirmation(MediaStore.createTrashRequest(resolver, listOf(uri), true).intentSender)
            }.getOrDefault(RemovalStep.Failed)
            !canWriteLegacy() -> RemovalStep.NeedsWritePermission
            else -> delete(track)
        }
    }

    /** The system dialog returned OK. */
    suspend fun onSystemConfirmed(track: Track): RemovalStep =
        if (mode == RemovalMode.Trash) RemovalStep.Removed else withContext(Dispatchers.IO) { delete(track) }

    private fun delete(track: Track): RemovalStep = try {
        val uri = track.contentUri.toUri()
        val file = filePath(uri)?.let(::File)
        when {
            resolver.delete(uri, null, null) == 0 -> RemovalStep.Failed
            file != null && file.exists() && !file.delete() -> RemovalStep.Failed
            else -> RemovalStep.Removed
        }
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            RemovalStep.NeedsSystemConfirmation(e.userAction.actionIntent.intentSender)
        } else {
            RemovalStep.Failed
        }
    }

    // Readable before Android 11 (legacy storage on Android 10), which is the only time it is used.
    @Suppress("DEPRECATION")
    private fun filePath(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun canWriteLegacy(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
}
