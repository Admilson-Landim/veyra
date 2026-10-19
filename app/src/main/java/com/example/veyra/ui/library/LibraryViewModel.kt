package com.example.veyra.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.media.MediaStoreTrackRepository
import com.example.veyra.data.media.TrackRepository
import com.example.veyra.data.media.sortedByTitle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class LibraryViewModel(
    private val repository: TrackRepository,
    // Sorting thousands of titles with a Collator is CPU work: keep it off the main thread.
    private val sortDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    private fun libraryState() =
        repository.tracks()
            .map { tracks ->
                if (tracks.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(tracks.sortedByTitle())
            }
            .flowOn(sortDispatcher)
            .onStart<LibraryUiState> { emit(LibraryUiState.Loading) }
            // e.g. SecurityException if the permission is revoked mid-query; retry() restarts it.
            .catch { emit(LibraryUiState.Error) }

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
                LibraryViewModel(MediaStoreTrackRepository(this[APPLICATION_KEY]!!.contentResolver))
            }
        }
    }
}
