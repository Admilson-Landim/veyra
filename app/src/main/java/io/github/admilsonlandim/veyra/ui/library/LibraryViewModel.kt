package io.github.admilsonlandim.veyra.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.admilsonlandim.veyra.data.media.LibraryView
import io.github.admilsonlandim.veyra.data.media.MediaStoreTrackRepository
import io.github.admilsonlandim.veyra.data.media.SearchQuery
import io.github.admilsonlandim.veyra.data.media.TrackRepository
import io.github.admilsonlandim.veyra.data.media.TrackSort
import io.github.admilsonlandim.veyra.data.media.filteredFor
import io.github.admilsonlandim.veyra.data.media.groupByArtist
import io.github.admilsonlandim.veyra.data.media.groupByFolder
import io.github.admilsonlandim.veyra.data.media.search
import io.github.admilsonlandim.veyra.data.media.searchArtists
import io.github.admilsonlandim.veyra.data.media.searchFolders
import io.github.admilsonlandim.veyra.data.media.sortedFor
import io.github.admilsonlandim.veyra.data.media.withCleanNames
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import io.github.admilsonlandim.veyra.data.settings.LibraryPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: TrackRepository,
    private val preferences: Flow<LibraryPreferences> = flowOf(LibraryPreferences()),
    // Sorting and filtering thousands of tracks is CPU work: keep it off the main thread.
    private val sortDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onSortChange: suspend (TrackSort) -> Unit = {},
    private val onViewChange: suspend (LibraryView) -> Unit = {},
) : ViewModel() {

    private val permission = MutableStateFlow(AudioPermission.Unknown)

    // Bumped by retry() to restart the tracks flow after an error.
    private val reloadRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> =
        combine(permission, reloadRequests) { permission, _ -> permission }
            .flatMapLatest { permission ->
                when (permission) {
                    AudioPermission.Unknown -> flowOf(LibraryUiState.Loading)
                    AudioPermission.Granted -> libraryState()
                    else -> flowOf(LibraryUiState.PermissionRequired(permission))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    // Names are cleaned once per library load (or when the setting changes), not on every
    // sort or filter change. Everything downstream (sort, search, playlists, the player and its
    // notification) sees the cleaned names.
    private fun displayedTracks() =
        combine(repository.tracks(), preferences.map { it.cleanNames }.distinctUntilChanged()) { all, clean ->
            if (clean) all.map { it.withCleanNames() } else all
        }

    // Preference changes (sort, excluded folders, minimum length) re-filter without re-querying.
    private fun libraryState() =
        combine(displayedTracks(), preferences) { all, prefs ->
            val minDurationMs = if (prefs.skipShortTracks) prefs.minDurationSeconds * 1_000L else null
            val visible = all.filteredFor(prefs.excludedFolders, minDurationMs).sortedFor(prefs.sort)
            if (visible.isEmpty()) {
                LibraryUiState.Empty(allTracks = all)
            } else {
                LibraryUiState.Content(
                    tracks = visible,
                    sort = prefs.sort,
                    allTracks = all,
                    view = prefs.view,
                    folders = visible.groupByFolder(),
                    artists = visible.groupByArtist(),
                )
            }
        }
            .flowOn(sortDispatcher)
            .onStart<LibraryUiState> { emit(LibraryUiState.Loading) }
            // e.g. SecurityException if the permission is revoked mid-query; retry() restarts it.
            .catch { emit(LibraryUiState.Error) }

    // Null while the search is closed. Not saved: a new session starts without a search.
    private val searchQuery = MutableStateFlow<String?>(null)

    /**
     * Results for the open search, or null when it is closed or empty (the normal list shows).
     * Filtering waits for a short pause in typing and runs off the main thread; it also follows
     * library changes.
     */
    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<SearchResults?> =
        searchQuery
            .debounce { if (it.isNullOrBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .distinctUntilChanged()
            .combine(uiState) { text, state ->
                val query = text?.let(::SearchQuery)
                if (query == null || query.isBlank || state !is LibraryUiState.Content) {
                    null
                } else {
                    SearchResults(
                        query = text,
                        tracks = state.tracks.search(query),
                        folders = state.folders.searchFolders(query),
                        artists = state.artists.searchArtists(query),
                    )
                }
            }
            .flowOn(sortDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The text typed in the search field, or null when the search is closed. */
    fun setSearchQuery(query: String?) {
        searchQuery.value = query
    }

    fun setSort(sort: TrackSort) {
        viewModelScope.launch { onSortChange(sort) }
    }

    fun setView(view: LibraryView) {
        viewModelScope.launch { onViewChange(view) }
    }

    /** Called on every resume with the current system grant (it may have changed in Settings). */
    fun onPermissionChecked(granted: Boolean) {
        permission.update { current ->
            when {
                granted -> AudioPermission.Granted
                // Keep what we learned from an earlier request in this session.
                current == AudioPermission.Denied || current == AudioPermission.PermanentlyDenied -> current
                else -> AudioPermission.NotRequested
            }
        }
    }

    /**
     * Called with the result of the system dialog. When the request was refused and the
     * system says no rationale is needed, it will not show the dialog again.
     */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        permission.value = when {
            granted -> AudioPermission.Granted
            shouldShowRationale -> AudioPermission.Denied
            else -> AudioPermission.PermanentlyDenied
        }
    }

    fun retry() {
        reloadRequests.update { it + 1 }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val prefs = AppPreferences.get(app)
                LibraryViewModel(
                    repository = MediaStoreTrackRepository(app.contentResolver),
                    preferences = prefs.library,
                    onSortChange = prefs::setSort,
                    onViewChange = prefs::setLibraryView,
                )
            }
        }
    }
}
