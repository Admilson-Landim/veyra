package com.example.veyra.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.playback.PlayerState
import com.example.veyra.playback.progressFraction
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.data.media.ArtworkSize
import com.example.veyra.ui.theme.components.TrackArtwork

/** Compact player pinned under the library list. Tapping it opens the full player. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(
                progress = { progressFraction(state.positionMs, state.durationMs) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClickLabel = stringResource(R.string.player_open_action), onClick = onOpen)
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                TrackArtwork(contentUri = state.artworkUri, size = 44.dp, artworkSize = ArtworkSize.Thumbnail)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title ?: stringResource(R.string.track_unknown_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist ?: stringResource(R.string.track_unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        painter = painterResource(if (state.showPause) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(
                            if (state.showPause) R.string.player_pause else R.string.player_play,
                        ),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MiniPlayerPreview() {
    VeyraTheme {
        MiniPlayer(
            state = PlayerState(
                isConnected = true, currentMediaId = "1", title = "Preview track",
                showPause = true, positionMs = 60_000, durationMs = 200_000,
            ),
            onOpen = {},
            onTogglePlayPause = {},
        )
    }
}
