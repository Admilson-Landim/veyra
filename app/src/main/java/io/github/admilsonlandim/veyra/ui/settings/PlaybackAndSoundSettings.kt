package io.github.admilsonlandim.veyra.ui.settings

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.audio.AudioEffectsSupport
import io.github.admilsonlandim.veyra.data.audio.BASS_STRENGTH_MAX
import io.github.admilsonlandim.veyra.data.audio.CUSTOM_PRESET
import io.github.admilsonlandim.veyra.data.audio.EqualizerCapabilities
import io.github.admilsonlandim.veyra.data.audio.EqualizerSettings
import io.github.admilsonlandim.veyra.data.audio.ReplayGainMode
import io.github.admilsonlandim.veyra.data.audio.formatBandFrequency
import io.github.admilsonlandim.veyra.data.clips.MIN_GLOBAL_REMAINING_MS
import io.github.admilsonlandim.veyra.data.settings.PlaybackPreferences
import java.util.Locale

private const val SECONDS_STEP = 5
private const val MAX_GLOBAL_SECONDS = 120

/** Settings > Playback: global start/end and skip silence. */
fun LazyListScope.playbackSection(
    playback: PlaybackPreferences,
    onChange: ((PlaybackPreferences) -> PlaybackPreferences) -> Unit,
) {
    item(key = "playback-header") { SectionHeader(stringResource(R.string.settings_section_playback)) }
    item(key = "skip-start") {
        SwitchRow(
            title = stringResource(R.string.settings_skip_start),
            detail = pluralStringResource(R.plurals.settings_skip_start_detail, playback.skipStartSeconds, playback.skipStartSeconds),
            checked = playback.skipStartEnabled,
            onCheckedChange = { on -> onChange { it.copy(skipStartEnabled = on) } },
        )
        if (playback.skipStartEnabled) {
            SecondsStepper(playback.skipStartSeconds) { s -> onChange { it.copy(skipStartSeconds = s) } }
        }
    }
    item(key = "skip-end") {
        SwitchRow(
            title = stringResource(R.string.settings_skip_end),
            detail = pluralStringResource(R.plurals.settings_skip_end_detail, playback.skipEndSeconds, playback.skipEndSeconds),
            checked = playback.skipEndEnabled,
            onCheckedChange = { on -> onChange { it.copy(skipEndEnabled = on) } },
        )
        if (playback.skipEndEnabled) {
            SecondsStepper(playback.skipEndSeconds) { s -> onChange { it.copy(skipEndSeconds = s) } }
        }
    }
    item(key = "global-note") {
        Text(
            text = (MIN_GLOBAL_REMAINING_MS / 1000).toInt().let { pluralStringResource(R.plurals.settings_global_note, it, it) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
    item(key = "skip-silence") {
        SwitchRow(
            title = stringResource(R.string.settings_skip_silence),
            detail = stringResource(R.string.settings_skip_silence_detail),
            checked = playback.skipSilence,
            onCheckedChange = { on -> onChange { it.copy(skipSilence = on) } },
        )
    }
}

@Composable
private fun SecondsStepper(seconds: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
        TextButton(onClick = { onChange(seconds - SECONDS_STEP) }, enabled = seconds > SECONDS_STEP) {
            Text(stringResource(R.string.settings_seconds_minus))
        }
        Text(
            stringResource(R.string.value_seconds, seconds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(onClick = { onChange(seconds + SECONDS_STEP) }, enabled = seconds < MAX_GLOBAL_SECONDS) {
            Text(stringResource(R.string.settings_seconds_plus))
        }
    }
}

/** Settings > Sound: the equalizer, with the device's own bands and presets. */
fun LazyListScope.soundSection(
    settings: EqualizerSettings,
    probe: AudioEffectsSupport.EqualizerProbe?,
    systemPanel: Intent?,
    onChange: ((EqualizerSettings) -> EqualizerSettings) -> Unit,
    onReset: (EqualizerCapabilities) -> Unit,
    onOpenSystemPanel: () -> Unit,
) {
    item(key = "sound-header") { SectionHeader(stringResource(R.string.settings_section_sound)) }
    item(key = "equalizer") {
        when (probe) {
            null -> Note(stringResource(R.string.eq_loading))
            AudioEffectsSupport.EqualizerProbe.Unsupported -> Note(stringResource(R.string.eq_unsupported))
            is AudioEffectsSupport.EqualizerProbe.Supported ->
                EqualizerPanel(settings, probe.capabilities, onChange, onReset)
        }
        if (systemPanel != null) {
            TextButton(onClick = onOpenSystemPanel, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.eq_system_panel))
            }
        }
        Note(stringResource(R.string.eq_vendor_note))
    }
}

/** Settings > Sound, under the equalizer: balanced volume and its ReplayGain mode. */
fun LazyListScope.balancedVolumeItems(
    playback: PlaybackPreferences,
    onChange: ((PlaybackPreferences) -> PlaybackPreferences) -> Unit,
) {
    item(key = "balanced-volume") {
        SwitchRow(
            title = stringResource(R.string.settings_balanced_volume),
            detail = stringResource(R.string.settings_balanced_volume_detail),
            checked = playback.balancedVolume,
            onCheckedChange = { on -> onChange { it.copy(balancedVolume = on) } },
        )
    }
    if (playback.balancedVolume) {
        item(key = "replay-gain-mode") {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.settings_balanced_mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReplayGainMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = playback.replayGainMode == mode,
                            onClick = { onChange { it.copy(replayGainMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ReplayGainMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                stringResource(
                                    if (mode == ReplayGainMode.Track) R.string.settings_balanced_mode_track else R.string.settings_balanced_mode_album,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerPanel(
    settings: EqualizerSettings,
    caps: EqualizerCapabilities,
    onChange: ((EqualizerSettings) -> EqualizerSettings) -> Unit,
    onReset: (EqualizerCapabilities) -> Unit,
) {
    Column {
        SwitchRow(
            title = stringResource(R.string.eq_title),
            detail = null,
            checked = settings.enabled,
            onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
        )
        if (!settings.enabled) return@Column

        PresetPicker(settings, caps) { preset -> onChange { it.withPreset(preset, caps) } }
        val levels = settings.levelsFor(caps)
        caps.bands.forEachIndexed { band, info ->
            BandSlider(
                label = formatBandFrequency(info.centerMilliHz),
                level = levels[band],
                range = caps.minLevel..caps.maxLevel,
                onLevel = { level -> onChange { it.withBandLevel(band, level, caps) } },
            )
        }
        if (caps.bassBoostSupported) {
            SwitchRow(
                title = stringResource(R.string.eq_bass_boost),
                detail = null,
                checked = settings.bassBoostEnabled,
                onCheckedChange = { on -> onChange { it.copy(bassBoostEnabled = on) } },
            )
            if (settings.bassBoostEnabled) {
                val percent = LocalResources.current
                LevelSlider(
                    label = stringResource(R.string.eq_bass_strength),
                    value = settings.bassStrength,
                    range = 0..BASS_STRENGTH_MAX,
                    format = { percent.getString(R.string.value_percent, it / 10) },
                    onValue = { strength -> onChange { it.copy(bassStrength = strength) } },
                )
            }
        }
        TextButton(onClick = { onReset(caps) }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.eq_reset))
        }
    }
}

@Composable
private fun PresetPicker(settings: EqualizerSettings, caps: EqualizerCapabilities, onPick: (Int) -> Unit) {
    if (caps.presets.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val current = caps.presets.getOrNull(settings.preset)?.name ?: stringResource(R.string.eq_preset_custom)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 24.dp, end = 12.dp),
    ) {
        Text(
            stringResource(R.string.eq_preset),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { open = true }) { Text(current) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.eq_preset_custom)) },
                    onClick = { open = false; onPick(CUSTOM_PRESET) },
                )
                caps.presets.forEachIndexed { index, preset ->
                    DropdownMenuItem(text = { Text(preset.name) }, onClick = { open = false; onPick(index) })
                }
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, level: Int, range: IntRange, onLevel: (Int) -> Unit) {
    // The app's language (may differ from the system's), for the decimal separator.
    val locale = LocalConfiguration.current.locales[0]
    LevelSlider(
        label = label,
        value = level,
        range = range,
        format = { formatDecibels(it, locale) },
        onValue = onLevel,
        accessibilityLabel = stringResource(R.string.eq_band, label),
    )
}

/** A labelled slider that only stores its value when the finger lifts. */
@Composable
private fun LevelSlider(
    label: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    onValue: (Int) -> Unit,
    accessibilityLabel: String = label,
) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Box(Modifier.weight(1f))
            Text(
                format(dragging.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onValue(dragging.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** Millibels to "+3,0 dB" style text. */
fun formatDecibels(millibels: Int, locale: Locale = Locale.getDefault()): String {
    val db = millibels / 100.0
    val sign = if (millibels > 0) "+" else ""
    return sign + String.format(locale, "%.1f dB", db)
}

/** The system's sound effects panel for the playback session, or null when the device has none. */
fun systemEffectsPanelIntent(context: Context): Intent? {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffectsSupport.playbackSessionId.value)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    return intent.takeIf { it.resolveActivity(context.packageManager) != null }
}
