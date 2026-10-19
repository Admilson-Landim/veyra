package com.example.veyra.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.data.media.ArtworkSize
import com.example.veyra.playback.PlayerState
import com.example.veyra.playback.RepeatMode
import com.example.veyra.playback.progressFraction
import com.example.veyra.ui.library.formatDuration
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.TrackArtwork

/** Callbacks shared by the full player controls. */
data class PlayerActions(
    val onTogglePlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleShuffle: () -> Unit,
)

/** Full-screen player, shown over the app from the mini-player. */
@Composable
fun PlayerScreen(
    state: PlayerState,
    actions: PlayerActions,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Artwork fits the screen: never wider than the content, nor taller than ~45% of it.
                val artworkDp = minOf(maxWidth - 48.dp, maxHeight * 0.45f, 360.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_expand_more),
                                contentDescription = stringResource(R.string.player_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TrackArtwork(contentUri = state.artworkUri, size = artworkDp, artworkSize = ArtworkSize.Large)
                    Spacer(Modifier.height(32.dp))
                    Column(Modifier.widthIn(max = 480.dp)) {
                        Text(
                            text = state.title ?: stringResource(R.string.track_unknown_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.artist ?: stringResource(R.string.track_unknown_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(24.dp))
                        SeekBar(state = state, onSeek = actions.onSeek)
                        Spacer(Modifier.height(16.dp))
                        TransportControls(state = state, actions = actions)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit) {
    // While dragging, show the thumb where the finger is; seek only on release.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: progressFraction(state.positionMs, state.durationMs)
    val shownPositionMs = (fraction * state.durationMs).toLong()
    val seekDescription = stringResource(R.string.player_seek)

    Slider(
        value = fraction,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
            dragFraction = null
        },
        enabled = state.durationMs > 0,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.semantics { contentDescription = seekDescription },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = formatDuration(shownPositionMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "-" + formatDuration(state.durationMs - shownPositionMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportControls(state: PlayerState, actions: PlayerActions) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onToggleShuffle) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(
                    if (state.shuffleEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off,
                ),
                tint = if (state.shuffleEnabled) active else inactive,
            )
        }
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.player_previous),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        FilledIconButton(
            onClick = actions.onTogglePlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                painter = painterResource(if (state.showPause) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(
                    if (state.showPause) R.string.player_pause else R.string.player_play,
                ),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.player_next),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = actions.onCycleRepeat) {
            Icon(
                painter = painterResource(
                    if (state.repeatMode == RepeatMode.One) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                ),
                contentDescription = stringResource(
                    when (state.repeatMode) {
                        RepeatMode.Off -> R.string.player_repeat_off
                        RepeatMode.All -> R.string.player_repeat_all
                        RepeatMode.One -> R.string.player_repeat_one
                    },
                ),
                tint = if (state.repeatMode == RepeatMode.Off) inactive else active,
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun PlayerScreenPreview() {
    VeyraTheme {
        PlayerScreen(
            state = PlayerState(
                isConnected = true, currentMediaId = "1", title = "Preview track", showPause = true,
                positionMs = 72_000, durationMs = 215_000, repeatMode = RepeatMode.All,
            ),
            actions = PlayerActions({}, {}, {}, {}, {}, {}),
            onClose = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
