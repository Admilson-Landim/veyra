package io.github.admilsonlandim.veyra.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.admilsonlandim.veyra.data.audio.AudioEffectsSupport
import io.github.admilsonlandim.veyra.data.audio.EqualizerCapabilities
import io.github.admilsonlandim.veyra.data.audio.EqualizerSettings
import io.github.admilsonlandim.veyra.data.media.ArtworkLoader
import io.github.admilsonlandim.veyra.data.media.isInFolder
import io.github.admilsonlandim.veyra.data.media.normalizeFolder
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import io.github.admilsonlandim.veyra.data.settings.AppearanceMode
import io.github.admilsonlandim.veyra.data.settings.DEFAULT_EXCLUDED_FOLDERS
import io.github.admilsonlandim.veyra.data.settings.LibraryPreferences
import io.github.admilsonlandim.veyra.data.settings.PlaybackPreferences
import io.github.admilsonlandim.veyra.ui.library.LibraryUiState
import io.github.admilsonlandim.veyra.ui.library.allTracksOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MIN_DURATION_STEP_SECONDS = 5
const val MIN_DURATION_LIMIT_SECONDS = 600

data class SettingsUiState(
    val library: LibraryPreferences = LibraryPreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val appearance: AppearanceMode = AppearanceMode.System,
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
        combine(preferences.library, library, preferences.playback, preferences.equalizer, preferences.appearance) { prefs, lib, playback, eq, appearance ->
            val folders = lib.allTracksOrNull.orEmpty()
                .mapNotNull { it.relativePath?.let(::normalizeFolder)?.takeIf(String::isNotEmpty) }
                .distinct()
                .filter { folder -> prefs.excludedFolders.none { isInFolder(folder, it) } }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            SettingsUiState(prefs, playback, eq, appearance, folders)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun excludeFolder(folder: String) = update { it + normalizeFolder(folder) }

    fun includeFolder(folder: String) = update { it - folder }

    fun resetFolders() {
        viewModelScope.launch { preferences.setExcludedFolders(DEFAULT_EXCLUDED_FOLDERS) }
    }

    fun setFixEncoding(enabled: Boolean) {
        viewModelScope.launch { preferences.setFixEncoding(enabled) }
    }

    fun setCleanNames(enabled: Boolean) {
        viewModelScope.launch { preferences.setCleanNames(enabled) }
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

    fun setAppearance(mode: AppearanceMode) {
        viewModelScope.launch { preferences.setAppearance(mode) }
    }

    fun updatePlayback(change: (PlaybackPreferences) -> PlaybackPreferences) {
        val next = change(state.value.playback)
        viewModelScope.launch { preferences.setPlayback(next) }
    }

    /** Device equalizer, read once off the main thread; null while reading. */
    private val _equalizerProbe = MutableStateFlow<AudioEffectsSupport.EqualizerProbe?>(null)
    val equalizerProbe: StateFlow<AudioEffectsSupport.EqualizerProbe?> = _equalizerProbe.asStateFlow()

    fun loadEqualizer(context: Context) {
        if (_equalizerProbe.value != null) return
        viewModelScope.launch { _equalizerProbe.value = AudioEffectsSupport.probe(context.applicationContext) }
    }

    fun updateEqualizer(change: (EqualizerSettings) -> EqualizerSettings) {
        val next = change(state.value.equalizer)
        viewModelScope.launch { preferences.setEqualizer(next) }
    }

    /** "Reset": flat bands, no preset, bass boost off; the on/off switch is kept. */
    fun resetEqualizer(capabilities: EqualizerCapabilities) = updateEqualizer {
        EqualizerSettings(enabled = it.enabled, bandLevels = List(capabilities.bands.size) { 0 })
    }

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
