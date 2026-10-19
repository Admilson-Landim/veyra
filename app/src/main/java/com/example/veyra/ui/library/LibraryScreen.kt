package com.example.veyra.ui.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.veyra.R
import com.example.veyra.data.media.Track
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.ArtworkPlaceholder
import com.example.veyra.ui.theme.components.VeyraEmptyState
import com.example.veyra.ui.theme.components.VeyraSection

// READ_MEDIA_AUDIO replaced READ_EXTERNAL_STORAGE for audio on Android 13 (API 33).
private val AudioPermissionName =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** Library section: owns the permission flow and wires the ViewModel to [LibraryContent]. */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context.findActivity()
        val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, AudioPermissionName)
        viewModel.onPermissionResult(granted, rationale)
    }

    // Runs on first display and every time the app returns to the foreground,
    // so a permission changed in system settings is picked up.
    LifecycleResumeEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(context, AudioPermissionName) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(granted)
        onPauseOrDispose { }
    }

    LibraryContent(
        state = state,
        onRequestPermission = { permissionLauncher.launch(AudioPermissionName) },
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VeyraSection(title = stringResource(R.string.library_title), modifier = modifier) {
        when (state) {
            LibraryUiState.Loading -> LoadingIndicator()

            is LibraryUiState.PermissionRequired -> PermissionRequest(
                permission = state.permission,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )

            LibraryUiState.Empty -> VeyraEmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
            )

            LibraryUiState.Error -> VeyraEmptyState(
                title = stringResource(R.string.library_error_title),
                message = stringResource(R.string.library_error_message),
                action = {
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.library_retry_action))
                    }
                },
            )

            is LibraryUiState.Content -> TrackList(state.tracks)
        }
    }
}

@Composable
private fun LoadingIndicator() {
    val description = stringResource(R.string.library_loading)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun PermissionRequest(
    permission: AudioPermission,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (titleRes, messageRes) = when (permission) {
        AudioPermission.Denied ->
            R.string.library_permission_denied_title to R.string.library_permission_denied_message
        AudioPermission.PermanentlyDenied ->
            R.string.library_permission_blocked_title to R.string.library_permission_blocked_message
        else -> R.string.library_permission_title to R.string.library_permission_message
    }
    VeyraEmptyState(
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        action = {
            if (permission == AudioPermission.PermanentlyDenied) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.library_open_settings_action))
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.library_permission_action))
                }
            }
        },
    )
}

@Composable
private fun TrackList(tracks: List<Track>) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "track-count") {
            Text(
                text = pluralStringResource(R.plurals.library_track_count, tracks.size, tracks.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(track)
        }
    }
}

// Not clickable on purpose: playback does not exist yet (Phase 3).
@Composable
private fun TrackRow(track: Track) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            // TalkBack reads title, artist and duration as one item.
            .semantics(mergeDescendants = true) {},
    ) {
        ArtworkPlaceholder(size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.track_unknown_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: stringResource(R.string.track_unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun LibraryPermissionPreview() {
    VeyraTheme {
        LibraryContent(
            state = LibraryUiState.PermissionRequired(AudioPermission.NotRequested),
            onRequestPermission = {},
            onOpenSettings = {},
            onRetry = {},
        )
    }
}

// Preview-only sample; never shown in the app.
@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun LibraryContentPreview() {
    val sample = Track(
        id = 1, contentUri = "", title = "Preview track", artist = null, album = null, albumId = 0,
        durationMs = 225_000, displayName = null, relativePath = null, sizeBytes = 0, dateModifiedSeconds = 0,
    )
    VeyraTheme {
        LibraryContent(
            state = LibraryUiState.Content(listOf(sample)),
            onRequestPermission = {},
            onOpenSettings = {},
            onRetry = {},
        )
    }
}
