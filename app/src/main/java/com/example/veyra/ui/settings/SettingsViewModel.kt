package com.example.veyra.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.media.ArtworkLoader
import com.example.veyra.data.media.isInFolder
import com.example.veyra.data.media.normalizeFolder
import com.example.veyra.data.settings.AppPreferences
import com.example.veyra.data.settings.DEFAULT_EXCLUDED_FOLDERS
import com.example.veyra.data.settings.LibraryPreferences
import com.example.veyra.ui.library.LibraryUiState
import com.example.veyra.ui.library.allTracksOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MIN_DURATION_STEP_SECONDS = 5
const val MIN_DURATION_LIMIT_SECONDS = 600

data class SettingsUiState(
    val library: LibraryPreferences = LibraryPreferences(),
    /** Folders that hold music and are not excluded yet, for the "exclude a folder" picker. */
    val excludableFolders: List<String> = emptyList(),
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    library: Flow<LibraryUiState>,
    /** Called after a change that affects where covers come from, so they are looked up again. */
    private val onArtworkSourcesChanged: () -> Unit = {},
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(preferences.library, library) { prefs, lib ->
            val folders = lib.allTracksOrNull.orEmpty()
                .mapNotNull { it.relativePath?.let(::normalizeFolder)?.takeIf(String::isNotEmpty) }
                .distinct()
                .filter { folder -> prefs.excludedFolders.none { isInFolder(folder, it) } }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            SettingsUiState(prefs, folders)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun excludeFolder(folder: String) = update { it + normalizeFolder(folder) }

    fun includeFolder(folder: String) = update { it - folder }

    fun resetFolders() {
        viewModelScope.launch { preferences.setExcludedFolders(DEFAULT_EXCLUDED_FOLDERS) }
    }

    fun setSkipShortTracks(enabled: Boolean) {
        viewModelScope.launch { preferences.setSkipShortTracks(enabled) }
    }

    fun setUseDeviceImages(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDeviceImages(enabled)
            onArtworkSourcesChanged()
        }
    }

    /** The photos permission was just granted (Android 13+): covers can now be found. */
    fun onImagesPermissionGranted() = setUseDeviceImages(true)

    fun changeMinDuration(deltaSeconds: Int) {
        val next = (state.value.library.minDurationSeconds + deltaSeconds)
            .coerceIn(MIN_DURATION_STEP_SECONDS, MIN_DURATION_LIMIT_SECONDS)
        viewModelScope.launch { preferences.setMinDurationSeconds(next) }
    }

    private fun update(change: (Set<String>) -> Set<String>) {
        val next = change(state.value.library.excludedFolders)
        viewModelScope.launch { preferences.setExcludedFolders(next) }
    }

    companion object {
        fun factory(library: Flow<LibraryUiState>): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                SettingsViewModel(AppPreferences.get(app), library) { ArtworkLoader.get(app).invalidate() }
            }
        }
    }
}
