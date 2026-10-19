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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.veyra.data.clips.ResolvedClip
import com.example.veyra.data.db.PlaylistSummary
import com.example.veyra.data.media.Track
import com.example.veyra.ui.clips.AdjustClipSheet
import com.example.veyra.ui.library.AudioPermissionMonitor
import com.example.veyra.ui.library.LibraryScreen
import com.example.veyra.ui.library.LibraryUiState
import com.example.veyra.ui.library.LibraryViewModel
import com.example.veyra.ui.player.MiniPlayer
import com.example.veyra.ui.player.PlayerActions
import com.example.veyra.ui.player.PlayerScreen
import com.example.veyra.ui.player.PlayerViewModel
import com.example.veyra.ui.playlists.AddToPlaylistSheet
import com.example.veyra.ui.playlists.DeletePlaylistDialog
import com.example.veyra.ui.playlists.PlaylistDetailActions
import com.example.veyra.ui.playlists.PlaylistDetailScreen
import com.example.veyra.ui.playlists.PlaylistDetailState
import com.example.veyra.ui.playlists.PlaylistNameDialog
import com.example.veyra.ui.playlists.PlaylistsScreen
import com.example.veyra.ui.playlists.PlaylistsViewModel
import com.example.veyra.ui.settings.SettingsScreen
import com.example.veyra.ui.theme.components.VeyraHeader
import kotlinx.coroutines.launch

// Width from which a navigation rail replaces the bottom bar (Material "medium" window class).
private val RailBreakpoint = 600.dp

/** Asks for a playlist name: a new playlist (optionally adding a track), or a rename. */
private sealed interface NameRequest {
    data class Create(val thenAdd: Track?) : NameRequest
    data class Rename(val playlistId: Long, val currentName: String) : NameRequest
}

@Composable
fun VeyraApp(
    /** Each increment asks to show the full player (e.g. the notification was tapped). */
    openPlayerRequests: Int = 0,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(libraryViewModel.uiState)),
    playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.factory(libraryViewModel.uiState)),
) {
    var destination by rememberSaveable { mutableStateOf(VeyraDestination.Library) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var adjustClipTrack by remember { mutableStateOf<Track?>(null) }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }

    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) {
            // The mini-player, and so the way back from the player, lives in Library and Playlists.
            if (destination == VeyraDestination.Settings) destination = VeyraDestination.Library
            playerOpen = true
        }
    }
    AudioPermissionMonitor(libraryViewModel)

    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val clips by playerViewModel.clips.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    val onAddedToPlaylist: (String) -> Unit = { name -> showMessage(resources.getString(R.string.added_to_playlist, name)) }

    // The service already skips to the next item; here we only tell the user, discreetly.
    LaunchedEffect(playerViewModel) {
        playerViewModel.errors.collect { error ->
            val message = error.title?.let { resources.getString(R.string.player_error_named, it) }
                ?: resources.getString(R.string.player_error)
            snackbarHostState.showSnackbar(message)
        }
    }

    // The current track, as known by the library: needed to add it to a playlist from the player.
    val currentTrack = remember(libraryState, playerState.currentMediaId) {
        (libraryState as? LibraryUiState.Content)?.tracks?.firstOrNull { it.id.toString() == playerState.currentMediaId }
    }

    val showPlayer = playerOpen && playerState.hasMedia
    BackHandler(enabled = destination == VeyraDestination.Playlists && openPlaylistId != null) {
        openPlaylistId = null
    }
    // Declared last so it wins over the handler above while the player is open.
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
                    if (destination == VeyraDestination.Settings) {
                        SettingsScreen(contentModifier)
                    } else {
                        // Library and Playlists share the mini-player at the bottom.
                        Column(contentModifier) {
                            val sectionModifier = Modifier.weight(1f)
                            if (destination == VeyraDestination.Library) {
                                LibraryScreen(
                                    currentMediaId = playerState.currentMediaId,
                                    onTrackClick = playerViewModel::play,
                                    onAddToPlaylist = { addToPlaylistTrack = it },
                                    onAdjustClip = { adjustClipTrack = it },
                                    clips = clips,
                                    viewModel = libraryViewModel,
                                    modifier = sectionModifier,
                                )
                            } else {
                                PlaylistsTab(
                                    openPlaylistId = openPlaylistId,
                                    onOpenPlaylist = { openPlaylistId = it },
                                    playlists = playlists,
                                    viewModel = playlistsViewModel,
                                    currentMediaId = playerState.currentMediaId,
                                    onPlay = playerViewModel::play,
                                    onCreate = { nameRequest = NameRequest.Create(thenAdd = null) },
                                    onRename = { id, name -> nameRequest = NameRequest.Rename(id, name) },
                                    onAdjustClip = { adjustClipTrack = it },
                                    clips = clips,
                                    modifier = sectionModifier,
                                )
                            }
                            if (playerState.hasMedia) {
                                MiniPlayer(
                                    state = playerState,
                                    onOpen = { playerOpen = true },
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                )
                            }
                        }
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
                onAddToPlaylist = currentTrack?.let { track -> { addToPlaylistTrack = track } },
                onAdjustClip = currentTrack?.let { track -> { adjustClipTrack = track } },
                clip = currentTrack?.let { clips[it.id] },
            )
        }
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistSheet(
            trackTitle = track.title ?: track.displayName.orEmpty(),
            playlists = playlists,
            onSelect = { playlist ->
                addToPlaylistTrack = null
                playlistsViewModel.addTrack(playlist, track, onAddedToPlaylist)
            },
            onCreateNew = {
                addToPlaylistTrack = null
                nameRequest = NameRequest.Create(thenAdd = track)
            },
            onDismiss = { addToPlaylistTrack = null },
        )
    }

    adjustClipTrack?.let { track ->
        AdjustClipSheet(
            track = track,
            stored = clips[track.id],
            mainPlayerPositionMs = {
                if (playerState.currentMediaId == track.id.toString()) playerViewModel.absolutePositionMs() else null
            },
            onSave = { clip ->
                adjustClipTrack = null
                playerViewModel.saveClip(track, clip)
                showMessage(resources.getString(R.string.clip_saved))
            },
            onReset = {
                adjustClipTrack = null
                playerViewModel.resetClip(track)
                showMessage(resources.getString(R.string.clip_reset_done))
            },
            onDismiss = { adjustClipTrack = null },
        )
    }

    when (val request = nameRequest) {
        is NameRequest.Create -> PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create_action),
            existingNames = playlistsViewModel.otherNames(null),
            onConfirm = { name ->
                nameRequest = null
                playlistsViewModel.create(name, request.thenAdd) { created ->
                    if (request.thenAdd != null) onAddedToPlaylist(created)
                }
            },
            onDismiss = { nameRequest = null },
        )
        is NameRequest.Rename -> PlaylistNameDialog(
            title = stringResource(R.string.playlist_rename_title),
            confirmLabel = stringResource(R.string.playlist_save_action),
            existingNames = playlistsViewModel.otherNames(request.playlistId),
            initialName = request.currentName,
            onConfirm = { name ->
                nameRequest = null
                playlistsViewModel.rename(request.playlistId, name)
            },
            onDismiss = { nameRequest = null },
        )
        null -> Unit
    }
}

/** Playlists tab: the list, or the open playlist. */
@Composable
private fun PlaylistsTab(
    openPlaylistId: Long?,
    onOpenPlaylist: (Long?) -> Unit,
    playlists: List<PlaylistSummary>,
    viewModel: PlaylistsViewModel,
    currentMediaId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onCreate: () -> Unit,
    onRename: (playlistId: Long, currentName: String) -> Unit,
    onAdjustClip: (Track) -> Unit,
    clips: Map<Long, ResolvedClip>,
    modifier: Modifier = Modifier,
) {
    if (openPlaylistId == null) {
        PlaylistsScreen(
            playlists = playlists,
            onOpen = { onOpenPlaylist(it.id) },
            onCreate = onCreate,
            modifier = modifier,
        )
        return
    }
    val detailFlow = remember(openPlaylistId) { viewModel.detail(openPlaylistId) }
    val detail by detailFlow.collectAsStateWithLifecycle(PlaylistDetailState.Loading)
    var confirmDelete by rememberSaveable(openPlaylistId) { mutableStateOf(false) }

    when (val state = detail) {
        PlaylistDetailState.Loading -> Box(modifier)
        // Deleted (here or elsewhere): go back to the list.
        PlaylistDetailState.Missing -> LaunchedEffect(Unit) { onOpenPlaylist(null) }
        is PlaylistDetailState.Ready -> {
            PlaylistDetailScreen(
                state = state,
                currentMediaId = currentMediaId,
                actions = PlaylistDetailActions(
                    onBack = { onOpenPlaylist(null) },
                    onPlay = onPlay,
                    onRename = { onRename(openPlaylistId, state.playlist.name) },
                    onDelete = { confirmDelete = true },
                    onMove = { from, to -> viewModel.moveEntry(openPlaylistId, from, to) },
                    onRemove = { entryId -> viewModel.removeEntry(openPlaylistId, entryId) },
                    onAdjustClip = onAdjustClip,
                ),
                modifier = modifier,
                clips = clips,
            )
            if (confirmDelete) {
                DeletePlaylistDialog(
                    name = state.playlist.name,
                    onConfirm = {
                        confirmDelete = false
                        viewModel.delete(openPlaylistId)
                    },
                    onDismiss = { confirmDelete = false },
                )
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
