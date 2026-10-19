package com.example.veyra.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.veyra.R
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.VeyraEmptyState
import com.example.veyra.ui.theme.components.VeyraSection

// Placeholder section: settings are not implemented yet, and the screen says so.
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    VeyraSection(title = stringResource(R.string.settings_title), modifier = modifier) {
        VeyraEmptyState(
            title = stringResource(R.string.settings_empty_title),
            message = stringResource(R.string.settings_empty_message),
            icon = R.drawable.ic_nav_settings,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun SettingsScreenPreview() {
    VeyraTheme { SettingsScreen() }
}
