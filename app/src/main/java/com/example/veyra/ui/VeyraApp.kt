package com.example.veyra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.veyra.R
import com.example.veyra.ui.library.LibraryScreen
import com.example.veyra.ui.player.MiniPlayer
import com.example.veyra.ui.player.PlayerActions
import com.example.veyra.ui.player.PlayerScreen
import com.example.veyra.ui.player.PlayerViewModel
import com.example.veyra.ui.playlists.PlaylistsScreen
import com.example.veyra.ui.settings.SettingsScreen
import com.example.veyra.ui.theme.components.VeyraHeader

// Width from which a navigation rail replaces the bottom bar (Material "medium" window class).
private val RailBreakpoint = 600.dp

@Composable
fun VeyraApp(
    /** Each increment asks to show the full player (e.g. the notification was tapped). */
    openPlayerRequests: Int = 0,
    playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    var destination by rememberSaveable { mutableStateOf(VeyraDestination.Library) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) {
            // The mini-player, and so the way back from the player, lives in the Library.
            destination = VeyraDestination.Library
            playerOpen = true
        }
    }
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // The service already skips to the next item; here we only tell the user, discreetly.
    LaunchedEffect(playerViewModel) {
        playerViewModel.errors.collect { error ->
            val message = error.title?.let { resources.getString(R.string.player_error_named, it) }
                ?: resources.getString(R.string.player_error)
            snackbarHostState.showSnackbar(message)
        }
    }

    val showPlayer = playerOpen && playerState.hasMedia
    BackHandler(enabled = showPlayer) { playerOpen = false }

    val playerActions = PlayerActions(
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onNext = playerViewModel::skipToNext,
        onPrevious = playerViewModel::skipToPrevious,
        onSeek = playerViewModel::seekTo,
        onCycleRepeat = playerViewModel::cycleRepeatMode,
        onToggleShuffle = playerViewModel::toggleShuffle,
    )

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= RailBreakpoint

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { if (!showPlayer) SnackbarHost(snackbarHostState) },
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
                        VeyraDestination.Library -> Column(contentModifier) {
                            LibraryScreen(
                                currentMediaId = playerState.currentMediaId,
                                onTrackClick = playerViewModel::play,
                                modifier = Modifier.weight(1f),
                            )
                            if (playerState.hasMedia) {
                                MiniPlayer(
                                    state = playerState,
                                    onOpen = { playerOpen = true },
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                )
                            }
                        }
                        VeyraDestination.Playlists -> PlaylistsScreen(contentModifier)
                        VeyraDestination.Settings -> SettingsScreen(contentModifier)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PlayerScreen(
                state = playerState,
                actions = playerActions,
                onClose = { playerOpen = false },
                snackbarHostState = snackbarHostState,
            )
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
