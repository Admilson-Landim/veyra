package com.example.veyra.ui.clips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.data.clips.ClipMode
import com.example.veyra.data.clips.ClipValidation
import com.example.veyra.data.clips.GlobalClipRule
import com.example.veyra.data.clips.MIN_CLIP_LENGTH_MS
import com.example.veyra.data.clips.ResolvedClip
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.clips.globalClip
import com.example.veyra.data.clips.validateClip
import com.example.veyra.data.media.Track
import com.example.veyra.playback.ClipPreviewPlayer
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

private const val STEP_MS = 1_000L
private const val PREVIEW_MS = 5_000L

/**
 * "Adjust start and end" for one track, with three choices: follow the global rule (default),
 * a custom clip (Phase 7 editor; times are positions in the file), or always the whole file.
 *
 * @param stored the track's saved setting, if any (a custom clip may no longer fit the file).
 * @param global the current global rule, to describe what "use the global rule" means here.
 * @param mainPlayerPositionMs position in the file of the main player, or null when [track]
 *   is not the one playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustClipSheet(
    track: Track,
    stored: ResolvedClip?,
    global: GlobalClipRule,
    mainPlayerPositionMs: () -> Long?,
    onSave: (TrackClip) -> Unit,
    onUseGlobal: () -> Unit,
    onFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable(track.id) {
        mutableStateOf(
            when (stored?.mode) {
                null -> SheetMode.Global
                ClipMode.Custom -> SheetMode.Custom
                ClipMode.Full -> SheetMode.Full
            },
        )
    }
    val duration = track.durationMs
    val initial = stored?.takeIf { it.isValid && it.mode == ClipMode.Custom }?.clip
    var startMs by rememberSaveable(track.id) { mutableLongStateOf(initial?.startMs ?: 0L) }
    var endMs by rememberSaveable(track.id) { mutableLongStateOf(initial?.endMs ?: duration) }
    val validation = validateClip(startMs, endMs, duration)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.clip_adjust),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = track.title ?: track.displayName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (stored != null && !stored.isValid) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.clip_stored_invalid,
                        formatClipTime(stored.clip.startMs),
                        formatClipTime(stored.clip.endMs ?: duration),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            ModeOption(
                selected = mode == SheetMode.Global,
                title = stringResource(R.string.clip_mode_global),
                detail = globalDescription(global, duration),
                onClick = { mode = SheetMode.Global },
            )
            ModeOption(
                selected = mode == SheetMode.Custom,
                title = stringResource(R.string.clip_mode_custom),
                detail = null,
                onClick = { mode = SheetMode.Custom },
            )
            ModeOption(
                selected = mode == SheetMode.Full,
                title = stringResource(R.string.clip_mode_full),
                detail = stringResource(R.string.clip_mode_full_detail),
                onClick = { mode = SheetMode.Full },
            )

            if (mode == SheetMode.Custom) {
                Spacer(Modifier.height(16.dp))
                ClipRangeEditor(
                    track = track,
                    startMs = startMs,
                    endMs = endMs,
                    onStartChange = { startMs = it },
                    onEndChange = { endMs = it },
                    mainPlayerPositionMs = mainPlayerPositionMs,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.clip_cancel)) }
                Button(
                    onClick = {
                        when (mode) {
                            SheetMode.Global -> onUseGlobal()
                            SheetMode.Custom -> onSave(TrackClip(startMs, endMs))
                            SheetMode.Full -> onFull()
                        }
                    },
                    enabled = mode != SheetMode.Custom || validation == ClipValidation.Valid,
                ) { Text(stringResource(R.string.clip_save)) }
            }
        }
    }
}

/**
 * Range editor for a section of [track] (positions in the file): range slider, ±1 s fine tuning,
 * "set start/end here" and short previews of the start and the end. Shared by "Adjust start and
 * end" and by "Set as ringtone".
 *
 * @param mainPlayerPositionMs position in the file of the main player, or null when [track]
 *   is not the one playing.
 */
@Composable
fun ClipRangeEditor(
    track: Track,
    startMs: Long,
    endMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    mainPlayerPositionMs: () -> Long?,
) {
    val duration = track.durationMs
    val context = LocalContext.current
    val preview = remember { ClipPreviewPlayer(context) }
    DisposableEffect(preview) { onDispose { preview.release() } }

    var usedPreview by remember { mutableStateOf(false) }
    val validation = validateClip(startMs, endMs, duration)

    // Where "set start/end here" points: the excerpt being previewed, else the main player if it
    // is playing this track, else where the last excerpt stopped.
    fun positionNow(): Long? = when {
        preview.isPlaying -> preview.absolutePositionMs()
        mainPlayerPositionMs() != null -> mainPlayerPositionMs()
        usedPreview -> preview.absolutePositionMs()
        else -> null
    }?.coerceIn(0L, duration)

    var position by remember { mutableStateOf(positionNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            position = positionNow()
            delay(250)
        }
    }

    Column {
        val rangeLabel = stringResource(R.string.clip_range_label)
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            onValueChange = { range ->
                // 100 ms resolution: finer than anyone can place by finger, coarse enough to read.
                onStartChange((range.start / 100).roundToLong() * 100)
                onEndChange(((range.endInclusive / 100).roundToLong() * 100).coerceAtMost(duration))
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.semantics { contentDescription = rangeLabel },
        )
        Text(
            text = stringResource(R.string.clip_length, formatClipTime((endMs - startMs).coerceAtLeast(0))),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        BoundRow(
            label = stringResource(R.string.clip_start),
            valueMs = startMs,
            minusDescription = stringResource(R.string.clip_start_minus),
            plusDescription = stringResource(R.string.clip_start_plus),
            onMinus = { onStartChange((startMs - STEP_MS).coerceAtLeast(0)) },
            onPlus = { onStartChange((startMs + STEP_MS).coerceAtMost(duration)) },
        )
        BoundRow(
            label = stringResource(R.string.clip_end),
            valueMs = endMs,
            minusDescription = stringResource(R.string.clip_end_minus),
            plusDescription = stringResource(R.string.clip_end_plus),
            onMinus = { onEndChange((endMs - STEP_MS).coerceAtLeast(0)) },
            onPlus = { onEndChange((endMs + STEP_MS).coerceAtMost(duration)) },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = position?.let { stringResource(R.string.clip_position, formatClipTime(it)) }
                ?: stringResource(R.string.clip_position_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Same height for both, even when one label wraps to two lines.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            OutlinedButton(
                onClick = { positionNow()?.let(onStartChange) },
                enabled = position != null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { Text(stringResource(R.string.clip_set_start_here), textAlign = TextAlign.Center) }
            OutlinedButton(
                onClick = { positionNow()?.let(onEndChange) },
                enabled = position != null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) { Text(stringResource(R.string.clip_set_end_here), textAlign = TextAlign.Center) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (preview.isPlaying) {
                OutlinedButton(onClick = preview::stop, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.clip_preview_stop))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        usedPreview = true
                        preview.play(track.contentUri, startMs, minOf(startMs + PREVIEW_MS, endMs))
                    },
                    enabled = validation == ClipValidation.Valid,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.clip_preview_start)) }
                OutlinedButton(
                    onClick = {
                        usedPreview = true
                        preview.play(track.contentUri, maxOf(endMs - PREVIEW_MS, startMs), endMs)
                    },
                    enabled = validation == ClipValidation.Valid,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.clip_preview_end)) }
            }
        }

        validationMessage(validation)?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

private enum class SheetMode { Global, Custom, Full }

@Composable
private fun ModeOption(selected: Boolean, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** What "use the global rule" does for this track, in words. */
@Composable
private fun globalDescription(global: GlobalClipRule, durationMs: Long): String {
    if (!global.isActive) return stringResource(R.string.clip_mode_global_off)
    val clip = globalClip(global, durationMs) ?: return stringResource(R.string.clip_mode_global_too_short)
    return stringResource(
        R.string.clip_mode_global_range,
        formatClipTime(clip.startMs),
        formatClipTime(clip.endMs ?: durationMs),
    )
}

@Composable
private fun BoundRow(
    label: String,
    valueMs: Long,
    minusDescription: String,
    plusDescription: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatClipTime(valueMs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = minusDescription }) {
            Text(stringResource(R.string.clip_minus_second))
        }
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = plusDescription }) {
            Text(stringResource(R.string.clip_plus_second))
        }
    }
}

@Composable
private fun validationMessage(validation: ClipValidation): String? = when (validation) {
    ClipValidation.Valid -> null
    ClipValidation.NegativeStart -> stringResource(R.string.clip_error_negative)
    ClipValidation.StartNotBeforeEnd -> stringResource(R.string.clip_error_order)
    ClipValidation.TooShort -> (MIN_CLIP_LENGTH_MS / 1000).toInt().let { pluralStringResource(R.plurals.clip_error_too_short, it, it) }
    ClipValidation.EndBeyondDuration -> stringResource(R.string.clip_error_beyond)
    ClipValidation.UnknownDuration -> stringResource(R.string.clip_error_unknown_duration)
}

/** Small scissors badge for tracks with a clip; error-coloured when the clip no longer fits. */
@Composable
fun ClipBadge(clip: ResolvedClip?, modifier: Modifier = Modifier) {
    // Only custom clips are flagged in lists; "always whole file" changes nothing visible there.
    if (clip == null || clip.mode == ClipMode.Full) return
    Icon(
        painter = painterResource(R.drawable.ic_content_cut),
        contentDescription = stringResource(if (clip.isValid) R.string.clip_active else R.string.clip_invalid),
        tint = if (clip.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = modifier.size(16.dp),
    )
}
