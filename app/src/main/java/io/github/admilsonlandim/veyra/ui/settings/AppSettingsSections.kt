package io.github.admilsonlandim.veyra.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.admilsonlandim.veyra.BuildConfig
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.settings.AppearanceMode

/** Languages offered in Settings. An empty tag means "follow the system". */
private enum class AppLanguage(val tag: String) { System(""), English("en"), Portuguese("pt") }

/** Settings > Appearance: follow the system, dark or light. */
fun LazyListScope.appearanceSection(current: AppearanceMode, onSelect: (AppearanceMode) -> Unit) {
    item(key = "appearance-header") { SectionHeader(stringResource(R.string.settings_section_appearance)) }
    item(key = "appearance") {
        Column(Modifier.selectableGroup()) {
            AppearanceMode.entries.forEach { mode ->
                OptionRow(
                    label = stringResource(
                        when (mode) {
                            AppearanceMode.System -> R.string.appearance_system
                            AppearanceMode.Dark -> R.string.appearance_dark
                            AppearanceMode.Light -> R.string.appearance_light
                        },
                    ),
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

/**
 * Settings > Language. Uses AndroidX per-app languages: AppCompatDelegate.setApplicationLocales
 * stores the choice (in the system on Android 13+, where it also appears in the system's
 * "App languages" screen; via AppCompat on older versions) and recreates the activity.
 */
fun LazyListScope.languageSection() {
    item(key = "language-header") { SectionHeader(stringResource(R.string.settings_section_language)) }
    item(key = "language") {
        // Read once; choosing a language recreates the activity, so this is never stale.
        var current by remember {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            mutableStateOf(AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && tag.startsWith(it.tag) } ?: AppLanguage.System)
        }
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                OptionRow(
                    label = when (language) {
                        AppLanguage.System -> stringResource(R.string.language_system)
                        AppLanguage.English -> stringResource(R.string.language_en)
                        AppLanguage.Portuguese -> stringResource(R.string.language_pt)
                    },
                    selected = language == current,
                    onClick = {
                        current = language
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                    },
                )
            }
        }
    }
}

/** Settings > About: name, slogan, version and the privacy note. */
fun LazyListScope.aboutSection() {
    item(key = "about-header") { SectionHeader(stringResource(R.string.settings_section_about)) }
    item(key = "about") {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.brand_slogan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 24.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
