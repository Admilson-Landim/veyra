package io.github.admilsonlandim.veyra.ui.playlists

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.playlists.PLAYLIST_NAME_MAX_LENGTH
import io.github.admilsonlandim.veyra.data.playlists.PlaylistNameValidation
import io.github.admilsonlandim.veyra.data.playlists.validatePlaylistName

/**
 * Asks for a playlist name (create or rename). The confirm button is enabled only for a valid
 * name; the reason a name is invalid is shown once the user has typed something.
 */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    existingNames: List<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var text by rememberSaveable { mutableStateOf(initialName) }
    var edited by rememberSaveable { mutableStateOf(false) }
    val validation = validatePlaylistName(text, existingNames)
    val error = when (validation) {
        PlaylistNameValidation.Empty -> stringResource(R.string.playlist_name_empty)
        PlaylistNameValidation.TooLong -> pluralStringResource(R.plurals.playlist_name_too_long, PLAYLIST_NAME_MAX_LENGTH, PLAYLIST_NAME_MAX_LENGTH)
        PlaylistNameValidation.Duplicate -> stringResource(R.string.playlist_name_duplicate)
        is PlaylistNameValidation.Valid -> null
    }
    val confirm = { if (validation is PlaylistNameValidation.Valid) onConfirm(validation.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    edited = true
                },
                label = { Text(stringResource(R.string.playlist_name_label)) },
                singleLine = true,
                isError = edited && error != null,
                supportingText = { if (edited && error != null) Text(error) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = validation is PlaylistNameValidation.Valid) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel_action)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun DeletePlaylistDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete_title, name)) },
        text = { Text(stringResource(R.string.playlist_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.playlist_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel_action)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
