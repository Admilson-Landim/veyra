package com.example.veyra.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.veyra.ui.library.LibraryScreen
import com.example.veyra.ui.playlists.PlaylistsScreen
import com.example.veyra.ui.settings.SettingsScreen
import com.example.veyra.ui.theme.VeyraTheme
import com.example.veyra.ui.theme.components.VeyraHeader

// Width from which a navigation rail replaces the bottom bar (Material "medium" window class).
private val RailBreakpoint = 600.dp

@Composable
fun VeyraApp() {
    var destination by rememberSaveable { mutableStateOf(VeyraDestination.Library) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= RailBreakpoint

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                VeyraHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            },
            bottomBar = {
                if (!useRail) {
                    VeyraBottomBar(selected = destination, onSelect = { destination = it })
                }
            },
        ) { innerPadding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    VeyraRail(selected = destination, onSelect = { destination = it })
                }
                val contentModifier = Modifier.weight(1f)
                when (destination) {
                    VeyraDestination.Library -> LibraryScreen(contentModifier)
                    VeyraDestination.Playlists -> PlaylistsScreen(contentModifier)
                    VeyraDestination.Settings -> SettingsScreen(contentModifier)
                }
            }
        }
    }
}

@Composable
private fun VeyraBottomBar(
    selected: VeyraDestination,
    onSelect: (VeyraDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        VeyraDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun VeyraRail(
    selected: VeyraDestination,
    onSelect: (VeyraDestination) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
        // The Scaffold content padding already accounts for the system bars.
        windowInsets = WindowInsets(0),
    ) {
        VeyraDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Preview(name = "Phone", widthDp = 400, heightDp = 800)
@Preview(name = "Tablet", widthDp = 900, heightDp = 600)
@Composable
private fun VeyraAppPreview() {
    VeyraTheme { VeyraApp() }
}
