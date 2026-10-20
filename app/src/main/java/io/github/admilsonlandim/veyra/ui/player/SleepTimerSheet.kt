package io.github.admilsonlandim.veyra.ui.player

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.playback.SleepTimerState
import io.github.admilsonlandim.veyra.ui.library.formatDuration
import kotlinx.coroutines.delay

/** Preset durations in the sleep timer sheet. */
private val SLEEP_PRESETS_MINUTES = listOf(15, 30, 45, 60, 90)
private const val SLEEP_EXTEND_MINUTES = 15
private const val SLEEP_MAX_MINUTES = 480

/** What the sleep timer sheet can do; the service runs the timer itself. */
data class SleepTimerActions(
    /** Minutes, or null for "end of the current track". */
    val onStart: (Int?) -> Unit,
    val onExtend: (Int) -> Unit,
    val onCancel: () -> Unit,
)

/** Time left until [endsAtMs] (elapsed realtime), refreshed every second while shown. */
@Composable
fun rememberSleepRemainingMs(endsAtMs: Long): Long {
    val remaining by produceState(endsAtMs - SystemClock.elapsedRealtime(), endsAtMs) {
        while (true) {
            value = (endsAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            delay(1_000L - SystemClock.elapsedRealtime() % 1_000L)
        }
    }
    return remaining
}

/** "Pauses in 12:34" / "Pauses at the end of this track", or null when no timer is set. */
@Composable
fun sleepTimerStatus(state: SleepTimerState?): String? = when (state) {
    is SleepTimerState.At -> stringResource(R.string.sleep_timer_remaining, formatDuration(rememberSleepRemainingMs(state.endsAtMs)))
    SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_until_end)
    null -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(state: SleepTimerState?, actions: SleepTimerActions, onDismiss: () -> Unit) {
    var customOpen by rememberSaveable { mutableStateOf(false) }
    fun pick(action: () -> Unit) {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .semantics { heading() },
            )
            sleepTimerStatus(state)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.sleep_timer_fade_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (state != null) {
                if (state is SleepTimerState.At) {
                    SheetRow(stringResource(R.string.sleep_timer_extend)) { pick { actions.onExtend(SLEEP_EXTEND_MINUTES) } }
                }
                SheetRow(stringResource(R.string.sleep_timer_cancel)) { pick(actions.onCancel) }
            }
            SLEEP_PRESETS_MINUTES.forEach { minutes ->
                SheetRow(pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) { pick { actions.onStart(minutes) } }
            }
            SheetRow(stringResource(R.string.sleep_timer_end_of_track)) { pick { actions.onStart(null) } }
            SheetRow(stringResource(R.string.sleep_timer_custom)) { customOpen = true }
        }
    }
    if (customOpen) {
        CustomTimerDialog(
            onConfirm = { minutes ->
                customOpen = false
                pick { actions.onStart(minutes) }
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun CustomTimerDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..SLEEP_MAX_MINUTES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.sleep_timer_custom_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel_action)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
