package io.github.admilsonlandim.veyra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.admilsonlandim.veyra.data.clips.ClipMode
import io.github.admilsonlandim.veyra.data.clips.EffectiveClip
import io.github.admilsonlandim.veyra.data.clips.GlobalClipRule
import io.github.admilsonlandim.veyra.data.clips.ResolvedClip
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.clips.clipToApply
import io.github.admilsonlandim.veyra.data.clips.effectiveClip
import io.github.admilsonlandim.veyra.data.clips.resolveClips
import io.github.admilsonlandim.veyra.data.db.SavedQueueDao
import io.github.admilsonlandim.veyra.data.db.TrackSettingsDao
import io.github.admilsonlandim.veyra.data.db.TrackSettingsEntity
import io.github.admilsonlandim.veyra.data.db.VeyraDatabase
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.toTrackKey
import io.github.admilsonlandim.veyra.data.queue.SavedQueue
import io.github.admilsonlandim.veyra.data.queue.planRestore
import io.github.admilsonlandim.veyra.data.queue.positionInClip
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import io.github.admilsonlandim.veyra.data.settings.PlaybackModes
import io.github.admilsonlandim.veyra.playback.PlaybackError
import io.github.admilsonlandim.veyra.playback.PlayerConnection
import io.github.admilsonlandim.veyra.playback.PlayerState
import io.github.admilsonlandim.veyra.playback.toMediaItem
import io.github.admilsonlandim.veyra.ui.library.LibraryUiState
import io.github.admilsonlandim.veyra.ui.library.allTracksOrNull
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the [PlayerConnection] for the Activity: survives rotation, so the MediaController is
 * built once and released in [onCleared]. Also owns the per-track clips: it resolves them
 * against the library off the main thread and keeps the playing queue in sync with them.
 */
class PlayerViewModel(
    private val connection: PlayerConnection,
    private val settingsDao: TrackSettingsDao,
    private val queueDao: SavedQueueDao,
    private val library: Flow<LibraryUiState>,
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

    /** The global start/end rule (Settings > Playback). */
    val globalRule: StateFlow<GlobalClipRule> =
        preferences.playback
            .map { it.globalRule }
            .stateIn(viewModelScope, SharingStarted.Eagerly, GlobalClipRule())

    /** What [track] plays with right now: its own setting, the global rule or the whole file. */
    fun effectiveFor(track: Track): EffectiveClip =
        effectiveClip(clips.value[track.id], globalRule.value, track.durationMs)

    init {
        connection.connect()
        // A clip saved or reset, or a change of the global rule, applies right away to the whole
        // queue, current track included (it keeps playing at the same point of the file when
        // that point is still inside the new range). One code path for every change.
        viewModelScope.launch {
            combine(clips, globalRule) { resolved, global -> resolved to global }.collect { (resolved, global) ->
                connection.syncClips(
                    trackFor = { mediaId -> mediaId.toLongOrNull()?.let { libraryById.value[it] } },
                    clipFor = { track -> effectiveClip(resolved[track.id], global, track.durationMs).clip },
                )
            }
        }
        // Shuffle, repeat and the last queue survive app restarts: restore them into an idle
        // player (a running service keeps its own state), then save every mode change.
        viewModelScope.launch {
            val connected = connection.state.first { it.isConnected }
            val restored = if (connected.queueSize == 0) {
                preferences.playbackModes.first().also { connection.setModes(it.shuffle, it.repeat) }
            } else {
                null
            }
            if (connected.queueSize == 0) launch { restoreQueue() }
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

    /**
     * Loads the last saved queue, paused where it was left. Waits for the library (tracks are
     * matched by TrackKey) and the stored clips, so each item starts with its current start/end.
     * Skipped if nothing saved still exists, or if something was queued in the meantime.
     */
    private suspend fun restoreQueue() {
        val saved = withContext(Dispatchers.IO) {
            val keys = queueDao.items().map { it.key }
            val (index, position) = preferences.queuePosition()
            SavedQueue(keys, index, position)
        }
        if (saved.keys.isEmpty()) return
        // Waits through "permission required" too: the queue comes back once access is granted.
        val tracks = library.mapNotNull { it.allTracksOrNull }.first()
        val resolved = settingsDao.observeAll().first().map { it.toStoredClip() }
        val plan = withContext(Dispatchers.Default) {
            planRestore(saved, tracks)?.let { it to resolveClips(resolved, it.tracks) }
        } ?: return
        val (restore, restoredClips) = plan
        val global = preferences.playback.first().globalRule
        val clipFor = { track: Track -> effectiveClip(restoredClips[track.id], global, track.durationMs).clip }

        val current = restore.tracks[restore.startIndex]
        val applied = clipToApply(clipFor(current), current.durationMs)
        val position = if (applied == null) {
            restore.startPositionMs.takeIf { it < current.durationMs } ?: 0
        } else {
            positionInClip(restore.startPositionMs, applied.startMs, applied.endMs ?: current.durationMs)
        }
        connection.loadQueue(restore.tracks.map { it.toMediaItem(clipFor(it)) }, restore.startIndex, position)
    }

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null) =
        connection.playQueue(tracks, startIndex, shuffle) { track -> effectiveFor(track).clip }

    fun togglePlayPause() = connection.togglePlayPause()
    fun skipToNext() = connection.skipToNext()
    fun skipToPrevious() = connection.skipToPrevious()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun toggleShuffle() = connection.toggleShuffle()

    /** Current position in the file, ignoring the clip (for "set start/end here"). */
    fun absolutePositionMs(): Long? = connection.absolutePositionMs()

    /**
     * Stores a custom [clip] for [track], replacing any previous rows for it. A custom clip
     * covering the whole file (start 0, natural end) is stored as "always the whole file".
     */
    fun saveClip(track: Track, clip: TrackClip) {
        val normalized = if (clip.endMs == track.durationMs) clip.copy(endMs = null) else clip
        if (normalized.isDefault) return setFullTrack(track)
        replaceSetting(track, TrackSettingsEntity.from(track.toTrackKey(), normalized, clock(), ClipMode.Custom))
    }

    /** "Always play the whole file": ignores the global rule for this track. */
    fun setFullTrack(track: Track) =
        replaceSetting(track, TrackSettingsEntity.from(track.toTrackKey(), TrackClip(0, null), clock(), ClipMode.Full))

    private fun replaceSetting(track: Track, replacement: TrackSettingsEntity) {
        val previousRows = clips.value[track.id]?.rowIds.orEmpty()
        viewModelScope.launch { settingsDao.replace(previousRows, replacement) }
    }

    /** Back to the default: the track follows the global rule. */
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
                    queueDao = VeyraDatabase.get(app).savedQueueDao(),
                    library = library,
                    preferences = AppPreferences.get(app),
                )
            }
        }
    }
}
