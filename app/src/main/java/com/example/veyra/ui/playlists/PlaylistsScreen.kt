package com.example.veyra.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.veyra.R
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.VeyraEmptyState
import com.example.veyra.ui.theme.components.VeyraSection

// Placeholder section: playlists are not implemented yet, and the screen says so.
@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) {
    VeyraSection(title = stringResource(R.string.playlists_title), modifier = modifier) {
        VeyraEmptyState(
            title = stringResource(R.string.playlists_empty_title),
            message = stringResource(R.string.playlists_empty_message),
            icon = R.drawable.ic_nav_playlists,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun PlaylistsScreenPreview() {
    VeyraTheme { PlaylistsScreen() }
}
