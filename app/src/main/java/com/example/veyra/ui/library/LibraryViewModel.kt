package com.example.veyra.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.media.MediaStoreTrackRepository
import com.example.veyra.data.media.TrackRepository
import com.example.veyra.data.media.TrackSort
import com.example.veyra.data.media.filteredFor
import com.example.veyra.data.media.sortedFor
import com.example.veyra.data.settings.AppPreferences
import com.example.veyra.data.settings.LibraryPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class LibraryViewModel(
    private val repository: TrackRepository,
    private val preferences: Flow<LibraryPreferences> = flowOf(LibraryPreferences()),
    // Sorting and filtering thousands of tracks is CPU work: keep it off the main thread.
    private val sortDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onSortChange: suspend (TrackSort) -> Unit = {},
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

    // Preference changes (sort, excluded folders, minimum length) re-filter without re-querying.
    private fun libraryState() =
        combine(repository.tracks(), preferences) { all, prefs ->
            val minDurationMs = if (prefs.skipShortTracks) prefs.minDurationSeconds * 1_000L else null
            val visible = all.filteredFor(prefs.excludedFolders, minDurationMs).sortedFor(prefs.sort)
            if (visible.isEmpty()) {
                LibraryUiState.Empty(allTracks = all)
            } else {
                LibraryUiState.Content(tracks = visible, sort = prefs.sort, allTracks = all)
            }
        }
            .flowOn(sortDispatcher)
            .onStart<LibraryUiState> { emit(LibraryUiState.Loading) }
            // e.g. SecurityException if the permission is revoked mid-query; retry() restarts it.
            .catch { emit(LibraryUiState.Error) }

    /** "Play all": the visible list, in the visible order, from the first track. */
    fun playAllRequest(): QueueRequest? =
        (uiState.value as? LibraryUiState.Content)?.let { QueueRequest(it.tracks, startIndex = 0, shuffle = false) }

    /** "Shuffle": the visible list with the player's shuffle on, starting from a random track. */
    fun shuffleRequest(random: Random = Random.Default): QueueRequest? =
        (uiState.value as? LibraryUiState.Content)?.let {
            QueueRequest(it.tracks, startIndex = random.nextInt(it.tracks.size), shuffle = true)
        }

    fun setSort(sort: TrackSort) {
        viewModelScope.launch { onSortChange(sort) }
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val prefs = AppPreferences.get(app)
                LibraryViewModel(
                    repository = MediaStoreTrackRepository(app.contentResolver),
                    preferences = prefs.library,
                    onSortChange = prefs::setSort,
                )
            }
        }
    }
}
