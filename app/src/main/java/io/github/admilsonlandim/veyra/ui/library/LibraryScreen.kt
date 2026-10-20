package io.github.admilsonlandim.veyra.ui.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.clips.ResolvedClip
import io.github.admilsonlandim.veyra.data.media.ArtistGroup
import io.github.admilsonlandim.veyra.data.media.ArtworkSize
import io.github.admilsonlandim.veyra.data.media.FolderGroup
import io.github.admilsonlandim.veyra.data.media.LibraryView
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackSort
import io.github.admilsonlandim.veyra.data.media.groupByFolder
import io.github.admilsonlandim.veyra.ui.clips.ClipBadge
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme
import io.github.admilsonlandim.veyra.ui.theme.components.BrandTile
import io.github.admilsonlandim.veyra.ui.theme.components.GroupArtwork
import io.github.admilsonlandim.veyra.ui.theme.components.TrackArtwork
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraEmptyState
import io.github.admilsonlandim.veyra.ui.theme.components.VeyraSection

// READ_MEDIA_AUDIO replaced READ_EXTERNAL_STORAGE for audio on Android 13 (API 33).
private val AudioPermissionName =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Re-checks the audio permission on first display and every time the app returns to the
 * foreground (it may have changed in system settings). Placed at app level, so playlists know
 * which tracks exist even if the Library tab was never opened.
 */
@Composable
fun AudioPermissionMonitor(viewModel: LibraryViewModel) {
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(context, AudioPermissionName) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(granted)
        onPauseOrDispose { }
    }
}

/**
 * Library section: owns the permission request, the search and the open folder or artist, and
 * wires the ViewModel to [LibraryContent].
 *
 * @param handleBack false while something above the Library (the full player) owns the back
 *   button: its handlers are registered later and would otherwise take precedence.
 */
@Composable
fun LibraryScreen(
    currentMediaId: String?,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    /** Long-press or "more options" on a row. */
    onTrackOptions: (Track) -> Unit,
    /** "Play all" / "Shuffle": the queue to start, built from the list on screen. */
    onPlayQueue: (QueueRequest) -> Unit,
    modifier: Modifier = Modifier,
    clips: Map<Long, ResolvedClip> = emptyMap(),
    handleBack: Boolean = true,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()

    // The search text lives here, so typing is never delayed; the ViewModel only filters.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(searchOpen, query) { viewModel.setSearchQuery(if (searchOpen) query else null) }
    DisposableEffect(viewModel) { onDispose { viewModel.setSearchQuery(null) } }
    val closeSearch = {
        searchOpen = false
        query = ""
    }
    // Back closes the open folder or artist first, then the search, then leaves.
    BackHandler(enabled = handleBack && openGroup != null) { openGroup = null }
    BackHandler(enabled = handleBack && openGroup == null && searchOpen, onBack = closeSearch)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context.findActivity()
        val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, AudioPermissionName)
        viewModel.onPermissionResult(granted, rationale)
    }

    LibraryContent(
        state = state,
        onRequestPermission = { permissionLauncher.launch(AudioPermissionName) },
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
        onRetry = viewModel::retry,
        currentMediaId = currentMediaId,
        browse = LibraryBrowseState(
            searchOpen = searchOpen,
            query = query,
            results = results,
            openGroup = openGroup,
        ),
        browseActions = LibraryBrowseActions(
            onOpenSearch = { searchOpen = true },
            onCloseSearch = closeSearch,
            onQueryChange = { query = it },
            onViewChange = viewModel::setView,
            onOpenGroup = { openGroup = it },
        ),
        actions = LibraryListActions(
            onTrackClick = onTrackClick,
            onTrackOptions = onTrackOptions,
            onPlayQueue = onPlayQueue,
            onSortChange = viewModel::setSort,
        ),
        clips = clips,
        modifier = modifier,
    )
}

/** Actions of a track list besides the rows' own. */
private data class LibraryListActions(
    /** The queue is the list as shown, starting at the tapped track. */
    val onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    val onTrackOptions: (Track) -> Unit,
    val onPlayQueue: (QueueRequest) -> Unit,
    val onSortChange: (TrackSort) -> Unit,
)

/** Search and navigation inside the Library. [openGroup] is a folder or artist id (see [groupKey]). */
private data class LibraryBrowseState(
    val searchOpen: Boolean = false,
    val query: String = "",
    val results: SearchResults? = null,
    val openGroup: String? = null,
)

private data class LibraryBrowseActions(
    val onOpenSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onViewChange: (LibraryView) -> Unit,
    val onOpenGroup: (String?) -> Unit,
)

private fun groupKey(folder: FolderGroup) = "folder:${folder.id}"

private fun groupKey(artist: ArtistGroup) = "artist:${artist.id}"

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    currentMediaId: String?,
    browse: LibraryBrowseState,
    browseActions: LibraryBrowseActions,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
    modifier: Modifier = Modifier,
) {
    if (state is LibraryUiState.Content) {
        BrowseContent(state, currentMediaId, browse, browseActions, actions, clips, modifier)
        return
    }
    VeyraSection(title = stringResource(R.string.library_title), modifier = modifier) {
        when (state) {
            LibraryUiState.Loading -> LoadingIndicator()

            is LibraryUiState.PermissionRequired -> PermissionRequest(
                permission = state.permission,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )

            is LibraryUiState.Empty -> VeyraEmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
            )

            LibraryUiState.Error -> VeyraEmptyState(
                title = stringResource(R.string.library_error_title),
                message = stringResource(R.string.library_error_message),
                action = {
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.library_retry_action))
                    }
                },
            )

            is LibraryUiState.Content -> Unit
        }
    }
}

/** The loaded library: the open folder or artist, or the chosen view, searched or not. */
@Composable
private fun BrowseContent(
    state: LibraryUiState.Content,
    currentMediaId: String?,
    browse: LibraryBrowseState,
    browseActions: LibraryBrowseActions,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
    modifier: Modifier,
) {
    val openKey = browse.openGroup
    if (openKey != null) {
        val folder = state.folders.firstOrNull { groupKey(it) == openKey }
        val artist = if (folder == null) state.artists.firstOrNull { groupKey(it) == openKey } else null
        if (folder != null || artist != null) {
            GroupDetail(
                title = folder?.let { folderTitle(it) } ?: artist?.name ?: stringResource(R.string.track_unknown_artist),
                subtitle = folder?.shortPath(),
                tracks = folder?.tracks ?: artist!!.tracks,
                sort = state.sort,
                currentMediaId = currentMediaId,
                onBack = { browseActions.onOpenGroup(null) },
                actions = actions,
                clips = clips,
                modifier = modifier,
            )
            return
        }
        // The folder or artist is gone (deleted, excluded, renamed): back to the list.
        LaunchedEffect(openKey) { browseActions.onOpenGroup(null) }
    }

    val body: @Composable ColumnScope.() -> Unit = {
        BrowseBody(state, currentMediaId, browse, browseActions, actions, clips)
    }
    if (browse.searchOpen) {
        Column(modifier.fillMaxHeight()) {
            SearchField(browse.query, browseActions.onQueryChange, browseActions.onCloseSearch)
            body()
        }
    } else {
        VeyraSection(
            title = stringResource(R.string.library_title),
            modifier = modifier,
            actions = {
                IconButton(onClick = browseActions.onOpenSearch) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.library_search))
                }
            },
            content = body,
        )
    }
}

/** View selector and the chosen view: tracks, folders or artists, filtered by the search. */
@Composable
private fun BrowseBody(
    state: LibraryUiState.Content,
    currentMediaId: String?,
    browse: LibraryBrowseState,
    browseActions: LibraryBrowseActions,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
) {
    ViewSelector(state.view, browseActions.onViewChange)

    // Null while the search is closed, empty, or waiting for a pause in typing.
    val results = browse.results.takeIf { browse.searchOpen }
    val empty = when (state.view) {
        LibraryView.Tracks -> results?.tracks?.isEmpty()
        LibraryView.Folders -> results?.folders?.isEmpty()
        LibraryView.Artists -> results?.artists?.isEmpty()
    } == true
    when {
        empty -> VeyraEmptyState(
            title = stringResource(R.string.library_search_no_results_title),
            message = stringResource(R.string.library_search_no_results_message, results!!.query),
            icon = R.drawable.ic_search,
        )
        state.view == LibraryView.Tracks ->
            TrackList(results?.tracks ?: state.tracks, state.sort, currentMediaId, actions, clips)
        state.view == LibraryView.Folders -> GroupList(
            count = pluralCount(R.plurals.library_folder_count, (results?.folders ?: state.folders).size),
            groups = results?.folders ?: state.folders,
            key = ::groupKey,
        ) { folder ->
            GroupRow(
                title = folderTitle(folder),
                detail = listOfNotNull(
                    folder.shortPath(),
                    pluralCount(R.plurals.library_track_count, folder.tracks.size),
                ).joinToString(" · "),
                onClick = { browseActions.onOpenGroup(groupKey(folder)) },
            ) { BrandTile(size = 48.dp, icon = R.drawable.ic_folder) }
        }
        else -> GroupList(
            count = pluralCount(R.plurals.library_artist_count, (results?.artists ?: state.artists).size),
            groups = results?.artists ?: state.artists,
            key = ::groupKey,
        ) { artist ->
            GroupRow(
                title = artist.name ?: stringResource(R.string.track_unknown_artist),
                detail = pluralCount(R.plurals.library_track_count, artist.tracks.size),
                onClick = { browseActions.onOpenGroup(groupKey(artist)) },
            ) {
                GroupArtwork(
                    contentUris = artist.tracks.take(ARTIST_COVER_CANDIDATES).map { it.contentUri },
                    size = 48.dp,
                    artworkSize = ArtworkSize.Thumbnail,
                )
            }
        }
    }
}

// Tracks tried, in order, for an artist's cover: enough to skip a few files without one.
private const val ARTIST_COVER_CANDIDATES = 4

@Composable
private fun pluralCount(id: Int, count: Int): String = pluralStringResource(id, count, count)

@Composable
private fun folderTitle(folder: FolderGroup): String = folder.name ?: stringResource(
    if (folder.path == null) R.string.folder_unknown else R.string.folder_root,
)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.library_search_close))
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.library_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.library_search_clear))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun ViewSelector(view: LibraryView, onSelect: (LibraryView) -> Unit) {
    val label = stringResource(R.string.library_view_label)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
    ) {
        LibraryView.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == view,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, LibraryView.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(option.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun LibraryView.labelRes(): Int = when (this) {
    LibraryView.Tracks -> R.string.library_view_tracks
    LibraryView.Folders -> R.string.library_view_folders
    LibraryView.Artists -> R.string.library_view_artists
}

/** The tracks of a folder or artist, with a way back. */
@Composable
private fun GroupDetail(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    sort: TrackSort,
    currentMediaId: String?,
    onBack: () -> Unit,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
    modifier: Modifier,
) {
    Column(modifier.fillMaxHeight()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 24.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.library_back))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TrackList(tracks, sort, currentMediaId, actions, clips)
    }
}

/** A list of folders or artists under a count. */
@Composable
private fun <T> GroupList(
    count: String,
    groups: List<T>,
    key: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        items(groups, key = key) { row(it) }
    }
}

@Composable
private fun GroupRow(title: String, detail: String, onClick: () -> Unit, artwork: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.library_open_action), onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        artwork()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    val description = stringResource(R.string.library_loading)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun PermissionRequest(
    permission: AudioPermission,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (titleRes, messageRes) = when (permission) {
        AudioPermission.Denied ->
            R.string.library_permission_denied_title to R.string.library_permission_denied_message
        AudioPermission.PermanentlyDenied ->
            R.string.library_permission_blocked_title to R.string.library_permission_blocked_message
        else -> R.string.library_permission_title to R.string.library_permission_message
    }
    VeyraEmptyState(
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        action = {
            if (permission == AudioPermission.PermanentlyDenied) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.library_open_settings_action))
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.library_permission_action))
                }
            }
        },
    )
}

/**
 * Tracks with the count, sort and "Play all" / "Shuffle" header. Everything plays [tracks] as
 * shown: the whole library, a folder, an artist or search results, so "next" is always the row
 * below.
 */
@Composable
private fun TrackList(
    tracks: List<Track>,
    sort: TrackSort,
    currentMediaId: String?,
    actions: LibraryListActions,
    clips: Map<Long, ResolvedClip>,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ListHeader(tracks = tracks, sort = sort, actions = actions)
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id.toString() == currentMediaId,
                onClick = { actions.onTrackClick(tracks, index) },
                clip = clips[track.id],
                onOptions = { actions.onTrackOptions(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    clip: ResolvedClip?,
    onOptions: () -> Unit,
) {
    val nowPlaying = stringResource(R.string.track_now_playing)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .combinedClickable(
                onClickLabel = stringResource(R.string.track_play_action),
                onLongClickLabel = stringResource(R.string.track_options),
                onLongClick = onOptions,
                onClick = onClick,
            )
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            // TalkBack reads title, artist and duration as one item, plus "A tocar" when current.
            .semantics(mergeDescendants = true) {
                selected = isCurrent
                if (isCurrent) stateDescription = nowPlaying
            },
    ) {
        TrackArtwork(contentUri = track.contentUri, size = 48.dp, artworkSize = ArtworkSize.Thumbnail)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.track_unknown_title),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: stringResource(R.string.track_unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        ClipBadge(clip, Modifier.padding(end = 8.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onOptions) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.track_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Count, sort menu, and "Play all" / "Shuffle" above the list. */
@Composable
private fun ListHeader(tracks: List<Track>, sort: TrackSort, actions: LibraryListActions) {
    val trackCount = tracks.size
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.library_track_count, trackCount, trackCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                val current = stringResource(sort.labelRes())
                val sortDescription = stringResource(R.string.library_sort_current, current)
                TextButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.semantics { contentDescription = sortDescription },
                ) {
                    Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(current)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    TrackSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            leadingIcon = {
                                if (option == sort) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                }
                            },
                            onClick = {
                                sortMenuOpen = false
                                actions.onSortChange(option)
                            },
                        )
                    }
                }
            }
        }
        // Two equal halves, single-line labels: no word ever breaks mid-way on narrow screens.
        val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
        ) {
            Button(onClick = { playAllRequest(tracks)?.let(actions.onPlayQueue) }, contentPadding = buttonPadding, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_play_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { shuffleRequest(tracks)?.let(actions.onPlayQueue) },
                contentPadding = buttonPadding,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_shuffle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun TrackSort.labelRes(): Int = when (this) {
    TrackSort.Title -> R.string.sort_title
    TrackSort.Artist -> R.string.sort_artist
    TrackSort.Album -> R.string.sort_album
    TrackSort.DateAdded -> R.string.sort_date_added
    TrackSort.Duration -> R.string.sort_duration
}

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun LibraryPermissionPreview() {
    VeyraTheme {
        LibraryContent(
            state = LibraryUiState.PermissionRequired(AudioPermission.NotRequested),
            onRequestPermission = {},
            onOpenSettings = {},
            onRetry = {},
            currentMediaId = "1",
            browse = LibraryBrowseState(),
            browseActions = LibraryBrowseActions({}, {}, {}, {}, {}),
            actions = LibraryListActions({ _, _ -> }, {}, {}, {}),
            clips = emptyMap(),
        )
    }
}

// Preview-only sample; never shown in the app.
@Preview(showBackground = true, backgroundColor = 0xFF17181C)
@Composable
private fun LibraryContentPreview() {
    val sample = Track(
        id = 1, contentUri = "", title = "Preview track", artist = null, album = null, albumId = 0,
        durationMs = 225_000, displayName = null, relativePath = null, sizeBytes = 0, dateModifiedSeconds = 0,
    )
    VeyraTheme {
        LibraryContent(
            state = LibraryUiState.Content(listOf(sample), folders = listOf(sample).groupByFolder()),
            onRequestPermission = {},
            onOpenSettings = {},
            onRetry = {},
            currentMediaId = "1",
            browse = LibraryBrowseState(),
            browseActions = LibraryBrowseActions({}, {}, {}, {}, {}),
            actions = LibraryListActions({ _, _ -> }, {}, {}, {}),
            clips = emptyMap(),
        )
    }
}
