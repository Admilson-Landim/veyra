package io.github.admilsonlandim.veyra.ui.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.media.RemovalMode

/**
 * The app's own confirmation before removing a track's file, naming the title, artist and file
 * (the title shown may be cleaned from the file name). The system asks again afterwards on
 * Android 11+ (trash request); Android 10 and older only ask for the storage permission, once.
 * The confirm button uses the error colour in both modes: the file leaves the device either way.
 */
@Composable
fun RemoveTrackDialog(
    confirmation: RemovalConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val track = confirmation.track
    val trash = confirmation.mode == RemovalMode.Trash
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (trash) R.string.remove_trash_title else R.string.remove_delete_title)) },
        text = {
            Column {
                Text(
                    text = track.title ?: stringResource(R.string.track_unknown_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = track.artist ?: stringResource(R.string.track_unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = track.displayName ?: stringResource(R.string.properties_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(if (trash) R.string.remove_trash_message else R.string.remove_delete_message))
                if (confirmation.ringtones.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val sounds = confirmation.ringtones.sortedBy { it.ordinal }.map { stringResource(it.labelRes()) }
                    Text(
                        text = stringResource(R.string.remove_ringtone_warning, sounds.joinToString(", ")),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (trash) R.string.remove_trash_confirm else R.string.remove_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel_action)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
