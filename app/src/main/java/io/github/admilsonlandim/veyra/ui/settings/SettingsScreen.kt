package io.github.admilsonlandim.veyra.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.audio.AudioEffectsSupport
import io.github.admilsonlandim.veyra.data.media.deviceImagesSupported
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraSection

/**
 * Settings, organised in sections. This phase has the Library section; later phases add theirs
 * as further items of the same list.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingFolder by rememberSaveable { mutableStateOf(false) }
    val prefs = state.library
    val context = LocalContext.current
    val equalizerProbe by viewModel.equalizerProbe.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadEqualizer(context) }
    val sessionId by AudioEffectsSupport.playbackSessionId.collectAsStateWithLifecycle()
    val systemPanel = remember(sessionId) { systemEffectsPanelIntent(context) }


    VeyraSection(title = stringResource(R.string.settings_title), modifier = modifier) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "library-header") { SectionHeader(stringResource(R.string.settings_section_library)) }

            item(key = "folders-title") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_excluded_folders),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_excluded_folders_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (prefs.excludedFolders.isEmpty()) {
                item(key = "folders-none") {
                    Text(
                        stringResource(R.string.settings_no_excluded_folders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(prefs.excludedFolders.sortedWith(String.CASE_INSENSITIVE_ORDER), key = { "folder-$it" }) { folder ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 24.dp, end = 8.dp),
                ) {
                    Text(
                        folder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.includeFolder(folder) }) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.settings_remove_folder, folder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "folders-actions") {
                Row(Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = { pickingFolder = true }) { Text(stringResource(R.string.settings_add_folder)) }
                    TextButton(onClick = viewModel::resetFolders) { Text(stringResource(R.string.settings_reset_folders)) }
                }
            }

            // Only where it can work (Android 12 and older, see deviceImagesSupported).
            if (deviceImagesSupported) {
                item(key = "device-images") {
                    SwitchRow(
                        title = stringResource(R.string.settings_device_images),
                        detail = stringResource(R.string.settings_device_images_detail),
                        checked = prefs.useDeviceImages,
                        onCheckedChange = viewModel::setUseDeviceImages,
                    )
                }
            }

            item(key = "clean-names") {
                SwitchRow(
                    title = stringResource(R.string.settings_clean_names),
                    detail = stringResource(R.string.settings_clean_names_detail),
                    checked = prefs.cleanNames,
                    onCheckedChange = viewModel::setCleanNames,
                )
            }

            item(key = "fix-encoding") {
                SwitchRow(
                    title = stringResource(R.string.settings_fix_encoding),
                    detail = stringResource(R.string.settings_fix_encoding_detail),
                    checked = prefs.fixEncoding,
                    onCheckedChange = viewModel::setFixEncoding,
                )
            }

            item(key = "skip-short") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setSkipShortTracks(!prefs.skipShortTracks) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_skip_short),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            pluralStringResource(R.plurals.settings_skip_short_detail, prefs.minDurationSeconds, prefs.minDurationSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = prefs.skipShortTracks,
                        onCheckedChange = viewModel::setSkipShortTracks,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            if (prefs.skipShortTracks) {
                item(key = "min-duration") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        TextButton(
                            onClick = { viewModel.changeMinDuration(-MIN_DURATION_STEP_SECONDS) },
                            enabled = prefs.minDurationSeconds > MIN_DURATION_STEP_SECONDS,
                        ) { Text(stringResource(R.string.settings_min_duration_minus)) }
                        Text(
                            stringResource(R.string.value_seconds, prefs.minDurationSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        TextButton(
                            onClick = { viewModel.changeMinDuration(MIN_DURATION_STEP_SECONDS) },
                            enabled = prefs.minDurationSeconds < MIN_DURATION_LIMIT_SECONDS,
                        ) { Text(stringResource(R.string.settings_min_duration_plus)) }
                    }
                }
            }

            playbackSection(state.playback, viewModel::updatePlayback)

            soundSection(
                settings = state.equalizer,
                probe = equalizerProbe,
                systemPanel = systemPanel,
                onChange = viewModel::updateEqualizer,
                onReset = viewModel::resetEqualizer,
                onOpenSystemPanel = { systemPanel?.let(context::startActivity) },
            )
            balancedVolumeItems(state.playback, viewModel::updatePlayback)

            appearanceSection(state.appearance, viewModel::setAppearance)
            languageSection()
            aboutSection()
        }
    }

    if (pickingFolder) {
        AlertDialog(
            onDismissRequest = { pickingFolder = false },
            title = { Text(stringResource(R.string.settings_add_folder_title)) },
            text = {
                if (state.excludableFolders.isEmpty()) {
                    Text(stringResource(R.string.settings_add_folder_empty))
                } else {
                    LazyColumn {
                        items(state.excludableFolders, key = { it }) { folder ->
                            Text(
                                folder,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pickingFolder = false
                                        viewModel.excludeFolder(folder)
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingFolder = false }) { Text(stringResource(R.string.settings_close)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
internal fun SwitchRow(title: String, detail: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}
