package io.github.admilsonlandim.veyra.ui.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.clips.ClipValidation
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.clips.validateClip
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.ui.clips.ClipRangeEditor

/** Default length of a ringtone excerpt when the track has no clip of its own. */
private const val DEFAULT_EXCERPT_MS = 30_000L

/**
 * "Set as ringtone / notification / alarm sound": the whole song, or only a part of it. A part is
 * exported to a new file (the song itself is not changed); see [RingtoneExporter].
 *
 * @param suggested a range to start from, e.g. the track's own start/end clip.
 * @param onConfirm called with null for the whole song, or the chosen section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSheet(
    track: Track,
    target: RingtoneTarget,
    suggested: TrackClip?,
    mainPlayerPositionMs: () -> Long?,
    onConfirm: (excerpt: TrackClip?) -> Unit,
    onDismiss: () -> Unit,
) {
    val duration = track.durationMs
    val canExcerpt = RingtoneExporter.isSupported && duration > 0
    var usePart by rememberSaveable(track.id, target) { mutableStateOf(canExcerpt) }
    // Start from the track's own clip if it has one, else from the first 30 seconds.
    val initialStart = suggested?.startMs ?: 0L
    val initialEnd = suggested?.endMs ?: (initialStart + DEFAULT_EXCERPT_MS).coerceAtMost(duration)
    var startMs by rememberSaveable(track.id, target) { mutableLongStateOf(initialStart) }
    var endMs by rememberSaveable(track.id, target) { mutableLongStateOf(initialEnd) }
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
                text = stringResource(target.labelRes()),
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
            Spacer(Modifier.height(12.dp))

            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    title = stringResource(R.string.ringtone_use_part),
                    detail = stringResource(
                        if (canExcerpt) R.string.ringtone_use_part_detail else R.string.ringtone_part_unsupported,
                    ),
                    selected = usePart,
                    enabled = canExcerpt,
                    onClick = { usePart = true },
                )
                ChoiceRow(
                    title = stringResource(R.string.ringtone_use_whole),
                    detail = null,
                    selected = !usePart,
                    enabled = true,
                    onClick = { usePart = false },
                )
            }

            if (usePart) {
                Spacer(Modifier.height(12.dp))
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
                    onClick = { onConfirm(if (usePart) TrackClip(startMs, endMs) else null) },
                    enabled = !usePart || validation == ClipValidation.Valid,
                ) { Text(stringResource(R.string.ringtone_set_action)) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, detail: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
