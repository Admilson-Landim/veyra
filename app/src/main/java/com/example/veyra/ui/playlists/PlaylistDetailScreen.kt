package com.example.veyra.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.data.media.ArtworkSize
import com.example.veyra.data.media.Track
import com.example.veyra.ui.library.formatDuration
import com.example.veyra.ui.theme.components.TrackArtwork
import com.example.veyra.ui.theme.components.VeyraEmptyState

/** Callbacks of the playlist screen. Indexes are positions in the playlist. */
data class PlaylistDetailActions(
    val onBack: () -> Unit,
    val onPlay: (tracks: List<Track>, startIndex: Int) -> Unit,
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onRemove: (entryId: Long) -> Unit,
)

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailState.Ready,
    currentMediaId: String?,
    actions: PlaylistDetailActions,
    modifier: Modifier = Modifier,
) {
    val playable = state.playableTracks
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            IconButton(onClick = actions.onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.playlist_back))
            }
            Text(
                text = state.playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .semantics { heading() },
            )
            IconButton(onClick = actions.onRename) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.playlist_rename))
            }
            IconButton(onClick = actions.onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.playlist_delete))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.library_track_count, state.entries.size, state.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { actions.onPlay(playable, 0) }, enabled = playable.isNotEmpty()) {
                Text(stringResource(R.string.playlist_play_all))
            }
        }
        if (state.access == LibraryAccess.NoPermission) {
            Text(
                text = stringResource(R.string.playlist_library_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (state.entries.isEmpty()) {
            VeyraEmptyState(
                title = stringResource(R.string.playlist_empty_title),
                message = stringResource(R.string.playlist_empty_message),
                icon = R.drawable.ic_playlist_add,
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.entries, key = { _, item -> item.entry.id }) { index, item ->
                EntryRow(
                    item = item,
                    isCurrent = item.track != null && item.track.id.toString() == currentMediaId,
                    // While the library is still loading, don't flag entries as unavailable.
                    showUnavailable = state.access == LibraryAccess.Ready,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.entries.lastIndex,
                    onPlay = {
                        val track = item.track ?: return@EntryRow
                        actions.onPlay(playable, playable.indexOf(track))
                    },
                    onMoveUp = { actions.onMove(index, index - 1) },
                    onMoveDown = { actions.onMove(index, index + 1) },
                    onRemove = { actions.onRemove(item.entry.id) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    item: PlaylistEntryUi,
    isCurrent: Boolean,
    showUnavailable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val unavailableLabel = stringResource(R.string.playlist_track_unavailable)
    val nowPlaying = stringResource(R.string.track_now_playing)
    val unavailable = showUnavailable && !item.isAvailable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .clickable(
                enabled = item.isAvailable,
                onClickLabel = stringResource(R.string.track_play_action),
                onClick = onPlay,
            )
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {
                selected = isCurrent
                when {
                    unavailable -> stateDescription = unavailableLabel
                    isCurrent -> stateDescription = nowPlaying
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .alpha(if (unavailable) 0.45f else 1f),
        ) {
            TrackArtwork(contentUri = item.track?.contentUri, size = 48.dp, artworkSize = ArtworkSize.Thumbnail)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title ?: stringResource(R.string.track_unknown_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (unavailable) {
                        unavailableLabel
                    } else {
                        item.artist ?: stringResource(R.string.track_unknown_artist)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.track?.let {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = formatDuration(it.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.playlist_entry_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_move_up)) },
                    enabled = canMoveUp,
                    onClick = { menuOpen = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_move_down)) },
                    enabled = canMoveDown,
                    onClick = { menuOpen = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_remove)) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}
