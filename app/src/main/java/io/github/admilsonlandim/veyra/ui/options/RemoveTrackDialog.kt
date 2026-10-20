package io.github.admilsonlandim.veyra.ui.options

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.media.RemovalMode
import io.github.admilsonlandim.veyra.data.media.Track

/**
 * The app's own confirmation before removing a track's file. Afterwards the system asks too on
 * Android 11+ (trash request); on Android 10 and older only the storage permission is asked,
 * once, since those versions have no per-file dialog that works. It names the file as well as the
 * title, since the title shown may be cleaned from the file name.
 */
@Composable
fun RemoveTrackDialog(
    track: Track,
    mode: RemovalMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = track.title ?: stringResource(R.string.track_unknown_title)
    val file = track.displayName ?: stringResource(R.string.properties_unknown)
    val trash = mode == RemovalMode.Trash
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (trash) R.string.remove_trash_title else R.string.remove_delete_title)) },
        text = {
            Text(stringResource(if (trash) R.string.remove_trash_message else R.string.remove_delete_message, title, file))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (trash) R.string.remove_trash_confirm else R.string.remove_delete_confirm),
                    // A permanent delete is the one action here that cannot be taken back.
                    color = if (trash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel_action)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
