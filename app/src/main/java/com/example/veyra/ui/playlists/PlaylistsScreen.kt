package com.example.veyra.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.data.db.PlaylistSummary
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.DefaultArtwork
import com.example.veyra.ui.theme.components.VeyraEmptyState
import com.example.veyra.ui.theme.components.VeyraSection

/** The list of playlists, with a "new playlist" action. */
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    onOpen: (PlaylistSummary) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VeyraSection(title = stringResource(R.string.playlists_title), modifier = modifier) {
        if (playlists.isEmpty()) {
            VeyraEmptyState(
                title = stringResource(R.string.playlists_empty_title),
                message = stringResource(R.string.playlists_empty_message),
                icon = R.drawable.ic_nav_playlists,
                action = { Button(onClick = onCreate) { Text(stringResource(R.string.playlist_new)) } },
            )
            return@VeyraSection
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "new") {
                TextButton(
                    onClick = onCreate,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.playlist_new))
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist, onClick = { onOpen(playlist) })
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.playlist_open_action), onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        DefaultArtwork(
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.library_track_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun PlaylistsScreenPreview() {
    VeyraTheme {
        PlaylistsScreen(
            playlists = listOf(PlaylistSummary(1, "Preview playlist", 0, 12)),
            onOpen = {},
            onCreate = {},
        )
    }
}
