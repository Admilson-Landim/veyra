package com.example.veyra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.clips.ResolvedClip
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.clips.resolveClips
import com.example.veyra.data.db.TrackSettingsDao
import com.example.veyra.data.db.TrackSettingsEntity
import com.example.veyra.data.db.VeyraDatabase
import com.example.veyra.data.media.Track
import com.example.veyra.data.media.toTrackKey
import com.example.veyra.data.settings.AppPreferences
import com.example.veyra.data.settings.PlaybackModes
import com.example.veyra.playback.PlaybackError
import com.example.veyra.playback.PlayerConnection
import com.example.veyra.playback.PlayerState
import com.example.veyra.ui.library.LibraryUiState
import com.example.veyra.ui.library.allTracksOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the [PlayerConnection] for the Activity: survives rotation, so the MediaController is
 * built once and released in [onCleared]. Also owns the per-track clips: it resolves them
 * against the library off the main thread and keeps the playing queue in sync with them.
 */
class PlayerViewModel(
    private val connection: PlayerConnection,
    private val settingsDao: TrackSettingsDao,
    library: Flow<LibraryUiState>,
    private val preferences: AppPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val libraryTracks: StateFlow<List<Track>> =
        library
            .map { it.allTracksOrNull ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val libraryById: StateFlow<Map<Long, Track>> =
        libraryTracks
            .map { tracks -> tracks.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Clip per library track id. Built once per change of the settings table or the library,
     * so building a queue is a map lookup per track, never a database query.
     */
    val clips: StateFlow<Map<Long, ResolvedClip>> =
        combine(settingsDao.observeAll(), libraryTracks) { rows, tracks ->
            resolveClips(rows.map { it.toStoredClip() }, tracks)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        connection.connect()
        // A clip saved or reset while its track is queued (or playing) applies right away.
        viewModelScope.launch {
            clips.collect { resolved ->
                connection.syncClips(
                    trackFor = { mediaId -> mediaId.toLongOrNull()?.let { libraryById.value[it] } },
                    clipFor = { track -> resolved[track.id]?.clip },
                )
            }
        }
        // Shuffle and repeat survive app restarts: restore them into an idle player (a running
        // service keeps its own state), then save every change.
        viewModelScope.launch {
            val connected = connection.state.first { it.isConnected }
            val restored = if (connected.queueSize == 0) {
                preferences.playbackModes.first().also { connection.setModes(it.shuffle, it.repeat) }
            } else {
                null
            }
            connection.state
                .filter { it.isConnected }
                .map { PlaybackModes(it.shuffleEnabled, it.repeatMode) }
                .distinctUntilChanged()
                // Until the player reports the restored modes, the old ones must not be saved.
                .dropWhile { restored != null && it != restored }
                .collect { preferences.setPlaybackModes(it) }
        }
    }

    // WhileSubscribed with no timeout: the position ticker stops as soon as the UI
    // stops collecting (app in the background), and restarts when it is visible again.
    val state: StateFlow<PlayerState> =
        connection.stateWithProgress()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), connection.state.value)

    val errors: SharedFlow<PlaybackError> = connection.errors

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null) =
        connection.playQueue(tracks, startIndex, shuffle) { track -> clips.value[track.id]?.clip }

    fun togglePlayPause() = connection.togglePlayPause()
    fun skipToNext() = connection.skipToNext()
    fun skipToPrevious() = connection.skipToPrevious()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun toggleShuffle() = connection.toggleShuffle()

    /** Current position in the file, ignoring the clip (for "set start/end here"). */
    fun absolutePositionMs(): Long? = connection.absolutePositionMs()

    /**
     * Stores [clip] for [track], replacing any previous rows for it. A clip equal to the
     * default (start 0, natural end) removes the setting instead.
     */
    fun saveClip(track: Track, clip: TrackClip) {
        val normalized = if (clip.endMs == track.durationMs) clip.copy(endMs = null) else clip
        val previousRows = clips.value[track.id]?.rowIds.orEmpty()
        val replacement = if (normalized.isDefault) {
            null
        } else {
            TrackSettingsEntity.from(track.toTrackKey(), normalized, clock())
        }
        viewModelScope.launch { settingsDao.replace(previousRows, replacement) }
    }

    fun resetClip(track: Track) {
        val previousRows = clips.value[track.id]?.rowIds.orEmpty()
        if (previousRows.isEmpty()) return
        viewModelScope.launch { settingsDao.replace(previousRows, null) }
    }

    override fun onCleared() {
        connection.release()
    }

    companion object {
        /** [library] is the library state, used to match stored clips to tracks. */
        fun factory(library: Flow<LibraryUiState>): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                PlayerViewModel(
                    connection = PlayerConnection(app),
                    settingsDao = VeyraDatabase.get(app).trackSettingsDao(),
                    library = library,
                    preferences = AppPreferences.get(app),
                )
            }
        }
    }
}
