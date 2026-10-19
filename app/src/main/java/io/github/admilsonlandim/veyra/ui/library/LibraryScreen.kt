package io.github.admilsonlandim.veyra.ui.library

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import io.github.admilsonlandim.veyra.data.media.TrackSort
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.clips.ResolvedClip
import io.github.admilsonlandim.veyra.data.media.ArtworkSize
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.ui.clips.ClipBadge
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme
import io.github.admilsonlandim.veyra.ui.theme.components.TrackArtwork
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraEmptyState
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraSection

// READ_MEDIA_AUDIO replaced READ_EXTERNAL_STORAGE for audio on Android 13 (API 33).
private val AudioPermissionName =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Re-checks the audio permission on first display and every time the app returns to the
 * foreground (it may have changed in system settings). Placed at app level, so playlists know
 * which tracks exist even if the Library tab was never opened.
 */
@Composable
fun AudioPermissionMonitor(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(context, AudioPermissionName) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(granted)
        onPauseOrDispose { }
    }
}

/** Library section: owns the permission request and wires the ViewModel to [LibraryContent]. */
@Composable
fun LibraryScreen(
    currentMediaId: String?,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    /** Long-press or "more options" on a row. */
    onTrackOptions: (Track) -> Unit,
    /** "Play all" / "Shuffle": the queue to start, built by the ViewModel from the visible list. */
    onPlayQueue: (QueueRequest) -> Unit,
    modifier: Modifier = Modifier,
    clips: Map<Long, ResolvedClip> = emptyMap(),
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

    LibraryContent(
        state = state,
        onRequestPermission = { permissionLauncher.launch(AudioPermissionName) },
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
        onRetry = viewModel::retry,
        currentMediaId = currentMediaId,
        onTrackClick = onTrackClick,
        actions = LibraryListActions(
            onTrackOptions = onTrackOptions,
            onPlayAll = { viewModel.playAllRequest()?.let(onPlayQueue) },
            onShuffle = { viewModel.shuffleRequest()?.let(onPlayQueue) },
            onSortChange = viewModel::setSort,
        ),
        clips = clips,
        modifier = modifier,
    )
}

/** Actions of the track list besides playing a row. */
private data class LibraryListActions(
    val onTrackOptions: (Track) -> Unit,
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onSortChange: (TrackSort) -> Unit,
)

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    currentMediaId: String?,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
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

            is LibraryUiState.Empty -> VeyraEmptyState(
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

            is LibraryUiState.Content -> TrackList(state.tracks, state.sort, currentMediaId, onTrackClick, actions, clips)
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
private fun TrackList(
    tracks: List<Track>,
    sort: TrackSort,
    currentMediaId: String?,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ListHeader(trackCount = tracks.size, sort = sort, actions = actions)
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id.toString() == currentMediaId,
                // The queue is the library as currently shown, starting at the tapped track.
                onClick = { onTrackClick(tracks, index) },
                clip = clips[track.id],
                onOptions = { actions.onTrackOptions(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    clip: ResolvedClip?,
    onOptions: () -> Unit,
) {
    val nowPlaying = stringResource(R.string.track_now_playing)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .combinedClickable(
                onClickLabel = stringResource(R.string.track_play_action),
                onLongClickLabel = stringResource(R.string.track_options),
                onLongClick = onOptions,
                onClick = onClick,
            )
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            // TalkBack reads title, artist and duration as one item, plus "A tocar" when current.
            .semantics(mergeDescendants = true) {
                selected = isCurrent
                if (isCurrent) stateDescription = nowPlaying
            },
    ) {
        TrackArtwork(contentUri = track.contentUri, size = 48.dp, artworkSize = ArtworkSize.Thumbnail)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.track_unknown_title),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
        ClipBadge(clip, Modifier.padding(end = 8.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onOptions) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.track_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Count, sort menu, and "Play all" / "Shuffle" above the list. */
@Composable
private fun ListHeader(trackCount: Int, sort: TrackSort, actions: LibraryListActions) {
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.library_track_count, trackCount, trackCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                val current = stringResource(sort.labelRes())
                val sortDescription = stringResource(R.string.library_sort_current, current)
                TextButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.semantics { contentDescription = sortDescription },
                ) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(current)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    TrackSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            leadingIcon = {
                                if (option == sort) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                }
                            },
                            onClick = {
                                sortMenuOpen = false
                                actions.onSortChange(option)
                            },
                        )
                    }
                }
            }
        }
        // Two equal halves, single-line labels: no word ever breaks mid-way on narrow screens.
        val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
        ) {
            Button(onClick = actions.onPlayAll, contentPadding = buttonPadding, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_play_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = actions.onShuffle,
                contentPadding = buttonPadding,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_shuffle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun TrackSort.labelRes(): Int = when (this) {
    TrackSort.Title -> R.string.sort_title
    TrackSort.Artist -> R.string.sort_artist
    TrackSort.Album -> R.string.sort_album
    TrackSort.DateAdded -> R.string.sort_date_added
    TrackSort.Duration -> R.string.sort_duration
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
            currentMediaId = "1",
            onTrackClick = { _, _ -> },
            actions = LibraryListActions({}, {}, {}, {}),
            clips = emptyMap(),
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
            currentMediaId = "1",
            onTrackClick = { _, _ -> },
            actions = LibraryListActions({}, {}, {}, {}),
            clips = emptyMap(),
        )
    }
}
