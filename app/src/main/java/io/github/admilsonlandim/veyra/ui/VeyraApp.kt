package io.github.admilsonlandim.veyra.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.clips.ClipMode
import io.github.admilsonlandim.veyra.data.clips.ResolvedClip
import io.github.admilsonlandim.veyra.data.db.PlaylistSummary
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.ui.clips.AdjustClipSheet
import io.github.admilsonlandim.veyra.ui.library.AudioPermissionMonitor
import io.github.admilsonlandim.veyra.ui.library.LibraryScreen
import io.github.admilsonlandim.veyra.ui.library.LibraryUiState
import io.github.admilsonlandim.veyra.ui.library.LibraryViewModel
import io.github.admilsonlandim.veyra.ui.library.allTracksOrNull
import io.github.admilsonlandim.veyra.ui.options.RemovalViewModel
import io.github.admilsonlandim.veyra.ui.options.RingtoneResult
import io.github.admilsonlandim.veyra.ui.options.RingtoneSetter
import io.github.admilsonlandim.veyra.ui.options.RingtoneSheet
import io.github.admilsonlandim.veyra.ui.options.RingtoneTarget
import io.github.admilsonlandim.veyra.ui.options.RingtoneViewModel
import io.github.admilsonlandim.veyra.ui.options.TrackOptionsActions
import io.github.admilsonlandim.veyra.ui.options.TrackOptionsSheet
import io.github.admilsonlandim.veyra.ui.options.TrackPropertiesSheet
import io.github.admilsonlandim.veyra.ui.options.TrackRemovalHost
import io.github.admilsonlandim.veyra.ui.player.MiniPlayer
import io.github.admilsonlandim.veyra.ui.player.PlayerActions
import io.github.admilsonlandim.veyra.ui.player.PlayerScreen
import io.github.admilsonlandim.veyra.ui.player.PlayerViewModel
import io.github.admilsonlandim.veyra.ui.player.SleepTimerActions
import io.github.admilsonlandim.veyra.ui.player.SleepTimerSheet
import io.github.admilsonlandim.veyra.ui.player.ruleIndicatorFor
import io.github.admilsonlandim.veyra.ui.playlists.AddToPlaylistSheet
import io.github.admilsonlandim.veyra.ui.playlists.DeletePlaylistDialog
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistDetailActions
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistDetailScreen
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistDetailState
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistNameDialog
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistsScreen
import io.github.admilsonlandim.veyra.ui.playlists.PlaylistsViewModel
import io.github.admilsonlandim.veyra.ui.settings.SettingsScreen
import io.github.admilsonlandim.veyra.ui.settings.SettingsViewModel
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraHeader
import kotlinx.coroutines.launch

// Width from which a navigation rail replaces the bottom bar (Material "medium" window class).
private val RailBreakpoint = 600.dp

/** Asks for a playlist name: a new playlist (optionally adding a track, by id), or a rename. */
private sealed interface NameRequest {
    data class Create(val thenAddId: Long?) : NameRequest
    data class Rename(val playlistId: Long, val currentName: String) : NameRequest
}

/** Keeps the open name dialog through rotation and process death. */
private val NameRequestSaver = listSaver<NameRequest?, Any?>(
    save = { request ->
        when (request) {
            is NameRequest.Create -> listOf("create", request.thenAddId)
            is NameRequest.Rename -> listOf("rename", request.playlistId, request.currentName)
            null -> emptyList()
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "create" -> NameRequest.Create(saved[1] as Long?)
            "rename" -> NameRequest.Rename(saved[1] as Long, saved[2] as String)
            else -> null
        }
    },
)

@Composable
fun VeyraApp(
    /** Each increment asks to show the full player (e.g. the notification was tapped). */
    openPlayerRequests: Int = 0,
    libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(libraryViewModel.uiState)),
    playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.factory(libraryViewModel.uiState)),
    removalViewModel: RemovalViewModel = viewModel(factory = RemovalViewModel.Factory),
    ringtoneViewModel: RingtoneViewModel = viewModel(factory = RingtoneViewModel.Factory),
) {
    var destination by rememberSaveable { mutableStateOf(VeyraDestination.Library) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Open sheets and dialogs survive rotation and process death: they keep the track's id and
    // find the track again in the library (shown once the library is loaded).
    var addToPlaylistTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adjustClipTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    var optionsTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    var propertiesTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    var ringtonePermissionNeeded by rememberSaveable { mutableStateOf(false) }
    var sleepTimerOpen by rememberSaveable { mutableStateOf(false) }
    var ringtoneTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    var ringtoneTarget by rememberSaveable { mutableStateOf<RingtoneTarget?>(null) }
    val ringtoneInProgress by ringtoneViewModel.inProgress.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(libraryViewModel.uiState))
    val context = LocalContext.current
    var nameRequest by rememberSaveable(stateSaver = NameRequestSaver) { mutableStateOf(null) }

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
    val globalRule by playerViewModel.globalRule.collectAsStateWithLifecycle()
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

    val tracksById = remember(libraryState) { libraryState.allTracksOrNull?.associateBy { it.id }.orEmpty() }

    // The current track, as known by the library: needed to add it to a playlist from the player.
    val currentTrack = remember(tracksById, playerState.currentMediaId) {
        playerState.currentMediaId?.toLongOrNull()?.let(tracksById::get)
    }

    LaunchedEffect(ringtoneViewModel) {
        ringtoneViewModel.results.collect { result ->
            snackbarHostState.showSnackbar(
                when (result) {
                    is RingtoneResult.Set -> resources.getString(
                        when (result.target) {
                            RingtoneTarget.Call -> R.string.ringtone_set_call
                            RingtoneTarget.Notification -> R.string.ringtone_set_notification
                            RingtoneTarget.Alarm -> R.string.ringtone_set_alarm
                        },
                        result.track.title ?: result.track.displayName.orEmpty(),
                    )
                    RingtoneResult.Failed -> resources.getString(R.string.ringtone_failed)
                    RingtoneResult.Cancelled -> resources.getString(R.string.ringtone_cancelled)
                },
            )
        }
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
            // Short screens (phone in landscape): the brand header would take most of the height.
            val showBrandHeader = maxHeight >= 480.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { if (!showPlayer) SnackbarHost(snackbarHostState) },
                topBar = {
                    if (showBrandHeader) VeyraHeader(
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
                        SettingsScreen(settingsViewModel, contentModifier)
                    } else {
                        // Library and Playlists share the mini-player at the bottom.
                        Column(contentModifier) {
                            val sectionModifier = Modifier.weight(1f)
                            if (destination == VeyraDestination.Library) {
                                LibraryScreen(
                                    currentMediaId = playerState.currentMediaId,
                                    onTrackClick = playerViewModel::play,
                                    onTrackOptions = { optionsTrackId = it.id },
                                    onPlayQueue = { request ->
                                        playerViewModel.play(request.tracks, request.startIndex, request.shuffle)
                                    },
                                    clips = clips,
                                    handleBack = !showPlayer,
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
                                    onCreate = { nameRequest = NameRequest.Create(thenAddId = null) },
                                    onRename = { id, name -> nameRequest = NameRequest.Rename(id, name) },
                                    onTrackOptions = { optionsTrackId = it.id },
                                    clips = clips,
                                    modifier = sectionModifier,
                                )
                            }
                            if (playerState.hasMedia) {
                                MiniPlayer(
                                    state = playerState,
                                    onOpen = { playerOpen = true },
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                    onNext = playerViewModel::skipToNext,
                                    onPrevious = playerViewModel::skipToPrevious,
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
                onTrackOptions = currentTrack?.let { track -> { optionsTrackId = track.id } },
                onAdjustClip = currentTrack?.let { track -> { adjustClipTrackId = track.id } },
                rule = currentTrack?.let { ruleIndicatorFor(it, clips[it.id], globalRule) },
                onSleepTimer = { sleepTimerOpen = true },
            )
        }
    }

    addToPlaylistTrackId?.let(tracksById::get)?.let { track ->
        AddToPlaylistSheet(
            trackTitle = track.title ?: track.displayName.orEmpty(),
            playlists = playlists,
            onSelect = { playlist ->
                addToPlaylistTrackId = null
                playlistsViewModel.addTrack(playlist, track, onAddedToPlaylist)
            },
            onCreateNew = {
                addToPlaylistTrackId = null
                nameRequest = NameRequest.Create(thenAddId = track.id)
            },
            onDismiss = { addToPlaylistTrackId = null },
        )
    }

    optionsTrackId?.let(tracksById::get)?.let { track ->
        TrackOptionsSheet(
            track = track,
            actions = TrackOptionsActions(
                onAddToPlaylist = { addToPlaylistTrackId = it.id },
                onAdjustClip = { adjustClipTrackId = it.id },
                onProperties = { propertiesTrackId = it.id },
                // Whole song or only a part: chosen in the ringtone sheet; the permission is
                // checked when the user confirms.
                onSetRingtone = { t, target ->
                    ringtoneTrackId = t.id
                    ringtoneTarget = target
                },
                onRemove = removalViewModel::request,
            ),
            onDismiss = { optionsTrackId = null },
        )
    }
    propertiesTrackId?.let(tracksById::get)?.let { track ->
        TrackPropertiesSheet(track, onDismiss = { propertiesTrackId = null })
    }

    if (sleepTimerOpen) {
        SleepTimerSheet(
            state = playerState.sleepTimer,
            actions = SleepTimerActions(
                onStart = playerViewModel::startSleepTimer,
                onExtend = playerViewModel::extendSleepTimer,
                onCancel = playerViewModel::cancelSleepTimer,
            ),
            onDismiss = { sleepTimerOpen = false },
        )
    }

    TrackRemovalHost(
        viewModel = removalViewModel,
        // The library drops it by itself (MediaStore change); the player's queue must too.
        onRemoved = playerViewModel::removeFromQueue,
        onMessage = showMessage,
    )

    val ringtoneTrack = ringtoneTrackId?.let(tracksById::get)
    val target = ringtoneTarget
    if (ringtoneTrack != null && target != null) {
        val track: Track = ringtoneTrack
        val closeRingtoneSheet = {
            ringtoneTrackId = null
            ringtoneTarget = null
        }
        RingtoneSheet(
            track = track,
            target = target,
            suggested = clips[track.id]?.takeIf { it.isValid && it.mode == ClipMode.Custom }?.clip,
            mainPlayerPositionMs = {
                if (playerState.currentMediaId == track.id.toString()) playerViewModel.absolutePositionMs() else null
            },
            onConfirm = { excerpt ->
                if (!RingtoneSetter.canWrite(context)) {
                    // Explain first; the system page only offers an on/off switch. The sheet stays
                    // open with the chosen section, so the user can confirm again on return.
                    ringtonePermissionNeeded = true
                    return@RingtoneSheet
                }
                closeRingtoneSheet()
                // Runs in the ViewModel, so a rotation does not interrupt it (see RingtoneViewModel).
                ringtoneViewModel.set(track, target, excerpt)
            },
            onDismiss = closeRingtoneSheet,
        )
    }

    // Creating an excerpt takes a few seconds: block other actions until it is done, but let
    // the user stop it (reported with a message).
    if (ringtoneInProgress) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = ringtoneViewModel::cancel) { Text(stringResource(R.string.playlist_cancel_action)) }
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(20.dp))
                    Text(stringResource(R.string.ringtone_creating))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (ringtonePermissionNeeded) {
        AlertDialog(
            onDismissRequest = { ringtonePermissionNeeded = false },
            title = { Text(stringResource(R.string.ringtone_permission_title)) },
            text = { Text(stringResource(R.string.ringtone_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    ringtonePermissionNeeded = false
                    context.startActivity(RingtoneSetter.permissionIntent(context))
                }) { Text(stringResource(R.string.ringtone_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { ringtonePermissionNeeded = false }) {
                    Text(stringResource(R.string.playlist_cancel_action))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    adjustClipTrackId?.let(tracksById::get)?.let { track ->
        AdjustClipSheet(
            track = track,
            stored = clips[track.id],
            global = globalRule,
            mainPlayerPositionMs = {
                if (playerState.currentMediaId == track.id.toString()) playerViewModel.absolutePositionMs() else null
            },
            onSave = { clip ->
                adjustClipTrackId = null
                playerViewModel.saveClip(track, clip)
                showMessage(resources.getString(R.string.clip_saved))
            },
            onUseGlobal = {
                adjustClipTrackId = null
                playerViewModel.resetClip(track)
                showMessage(resources.getString(R.string.clip_reset_done))
            },
            onFull = {
                adjustClipTrackId = null
                playerViewModel.setFullTrack(track)
                showMessage(resources.getString(R.string.clip_saved))
            },
            onDismiss = { adjustClipTrackId = null },
        )
    }

    when (val request = nameRequest) {
        is NameRequest.Create -> PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create_action),
            existingNames = playlistsViewModel.otherNames(null),
            onConfirm = { name ->
                nameRequest = null
                val thenAdd = request.thenAddId?.let(tracksById::get)
                playlistsViewModel.create(name, thenAdd) { created ->
                    if (thenAdd != null) onAddedToPlaylist(created)
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
    onTrackOptions: (Track) -> Unit,
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
                    onTrackOptions = onTrackOptions,
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
