package io.github.admilsonlandim.veyra.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.clips.AppliedRule
import io.github.admilsonlandim.veyra.data.clips.ClipMode
import io.github.admilsonlandim.veyra.data.clips.GlobalClipRule
import io.github.admilsonlandim.veyra.data.clips.ResolvedClip
import io.github.admilsonlandim.veyra.data.clips.effectiveClip
import io.github.admilsonlandim.veyra.data.media.ArtworkSize
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.playback.PlayerState
import io.github.admilsonlandim.veyra.playback.RepeatMode
import io.github.admilsonlandim.veyra.playback.progressFraction
import io.github.admilsonlandim.veyra.ui.clips.formatClipTime
import io.github.admilsonlandim.veyra.ui.library.formatDuration
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme
import io.github.admilsonlandim.veyra.ui.theme.components.TrackArtwork

/** The discreet "which rule is playing" line under the artist. */
data class RuleIndicator(val text: String, val isError: Boolean = false)

/**
 * Describes how [track] is playing: its own clip, the global rule, or "whole file" when the
 * global rule is on but the track opts out. Null when nothing special applies.
 */
@Composable
fun ruleIndicatorFor(track: Track, stored: ResolvedClip?, global: GlobalClipRule): RuleIndicator? {
    if (stored?.mode == ClipMode.Custom && !stored.isValid) {
        return RuleIndicator(stringResource(R.string.clip_player_invalid), isError = true)
    }
    val effective = effectiveClip(stored, global, track.durationMs)
    val clip = effective.clip
    return when (effective.rule) {
        AppliedRule.Custom -> clip?.let {
            RuleIndicator(stringResource(R.string.clip_player_range, formatClipTime(it.startMs), formatClipTime(it.endMs ?: track.durationMs)))
        }
        AppliedRule.Global -> clip?.let {
            RuleIndicator(stringResource(R.string.player_rule_global, formatClipTime(it.startMs), formatClipTime(it.endMs ?: track.durationMs)))
        }
        AppliedRule.Full -> if (global.isActive) RuleIndicator(stringResource(R.string.player_rule_full)) else null
        AppliedRule.None -> null
    }
}

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
    /** Opens the track options sheet; null while the current track is not known in the library. */
    onTrackOptions: (() -> Unit)? = null,
    /** Same as [onTrackOptions], for the start/end editor. */
    onAdjustClip: (() -> Unit)? = null,
    /** Which start/end rule the current track uses, shown as a discreet line under the artist. */
    rule: RuleIndicator? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val topBar = @Composable { PlayerTopBar(onClose, onAdjustClip, onTrackOptions) }
                val details = @Composable { PlayerDetails(state, actions, rule) }
                // Landscape (wide and short): artwork on the left, everything else on the right,
                // so the controls are visible without scrolling.
                if (maxWidth > maxHeight && maxHeight < 600.dp) {
                    val artworkDp = minOf(maxHeight - 48.dp, maxWidth * 0.4f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                    ) {
                        PlayerArtwork(state, actions, artworkDp)
                        Spacer(Modifier.width(24.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            topBar()
                            details()
                        }
                    }
                } else {
                    // Artwork fits the screen: never wider than the content, nor taller than ~45% of it.
                    val artworkDp = minOf(maxWidth - 48.dp, maxHeight * 0.45f, 360.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    ) {
                        topBar()
                        Spacer(Modifier.height(16.dp))
                        PlayerArtwork(state, actions, artworkDp)
                        Spacer(Modifier.height(32.dp))
                        details()
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PlayerTopBar(onClose: () -> Unit, onAdjustClip: (() -> Unit)?, onTrackOptions: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(R.string.player_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onAdjustClip != null) {
            IconButton(onClick = onAdjustClip) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_cut),
                    contentDescription = stringResource(R.string.clip_adjust),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (onTrackOptions != null) {
            IconButton(onClick = onTrackOptions) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.track_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PlayerArtwork(state: PlayerState, actions: PlayerActions, size: Dp) {
    // Swipe the cover left/right to change track; the seek slider sits outside it.
    SwipeToSkip(
        onNext = actions.onNext,
        onPrevious = actions.onPrevious,
        nextLabel = stringResource(R.string.player_next),
        previousLabel = stringResource(R.string.player_previous),
    ) {
        TrackArtwork(contentUri = state.artworkUri, size = size, artworkSize = ArtworkSize.Large)
    }
}

/** Title, artist, rule line, seek bar and transport controls, in reading order for TalkBack. */
@Composable
private fun PlayerDetails(state: PlayerState, actions: PlayerActions, rule: RuleIndicator?) {
    Column(Modifier.widthIn(max = 480.dp)) {
        Text(
            text = state.title ?: stringResource(R.string.track_unknown_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
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
        if (rule != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = rule.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (rule.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
        SeekBar(state = state, onSeek = actions.onSeek)
        Spacer(Modifier.height(16.dp))
        TransportControls(state = state, actions = actions)
        Spacer(Modifier.height(24.dp))
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
