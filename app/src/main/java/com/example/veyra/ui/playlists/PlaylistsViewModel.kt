package com.example.veyra.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.db.PlaylistDao
import com.example.veyra.data.db.PlaylistEntity
import com.example.veyra.data.db.PlaylistSummary
import com.example.veyra.data.db.PlaylistTrackEntity
import com.example.veyra.data.db.VeyraDatabase
import com.example.veyra.data.media.Track
import com.example.veyra.data.media.TrackKeyResolver
import com.example.veyra.ui.library.LibraryUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/** What the playlist screen knows about the device library. */
enum class LibraryAccess { Ready, NoPermission, Loading }

/** A playlist entry matched (or not) against the current library. */
data class PlaylistEntryUi(val entry: PlaylistTrackEntity, val track: Track?) {
    val isAvailable: Boolean get() = track != null
    val title: String? get() = track?.title ?: entry.title ?: entry.displayName
    val artist: String? get() = track?.artist ?: entry.artist
}

sealed interface PlaylistDetailState {
    data object Loading : PlaylistDetailState
    /** The playlist no longer exists (e.g. just deleted). */
    data object Missing : PlaylistDetailState
    data class Ready(
        val playlist: PlaylistEntity,
        val entries: List<PlaylistEntryUi>,
        val access: LibraryAccess,
    ) : PlaylistDetailState {
        /** Unavailable entries are skipped: they cannot be played, and are kept in case the file returns. */
        val playableTracks: List<Track> get() = entries.mapNotNull { it.track }
    }
}

class PlaylistsViewModel(
    private val dao: PlaylistDao,
    private val library: Flow<LibraryUiState>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resolveDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    // Eager: name validation reads the current list even while the list screen is not shown.
    val playlists: StateFlow<List<PlaylistSummary>> =
        dao.observePlaylists()
            .map { list ->
                val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
                list.sortedWith { a, b -> collator.compare(a.name, b.name) }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Names of all playlists except [playlistId], for duplicate checks. */
    fun otherNames(playlistId: Long?): List<String> =
        playlists.value.filter { it.id != playlistId }.map { it.name }

    /** The playlist, its entries resolved against the library, and whether the library is readable. */
    fun detail(playlistId: Long): Flow<PlaylistDetailState> =
        combine(dao.observePlaylist(playlistId), dao.observeEntries(playlistId), library) { playlist, entries, lib ->
            if (playlist == null) return@combine PlaylistDetailState.Missing
            val (access, tracks) = when (lib) {
                is LibraryUiState.Content -> LibraryAccess.Ready to lib.tracks
                LibraryUiState.Empty -> LibraryAccess.Ready to emptyList()
                is LibraryUiState.PermissionRequired -> LibraryAccess.NoPermission to null
                LibraryUiState.Loading, LibraryUiState.Error -> LibraryAccess.Loading to null
            }
            val resolver = tracks?.let(::TrackKeyResolver)
            PlaylistDetailState.Ready(
                playlist = playlist,
                entries = entries.map { PlaylistEntryUi(it, resolver?.resolve(it.key)) },
                access = access,
            )
        }.flowOn(resolveDispatcher)

    /** Creates a playlist and, if [thenAdd] is given, adds that track to it. */
    fun create(name: String, thenAdd: Track? = null, onDone: (name: String) -> Unit = {}) {
        viewModelScope.launch {
            val now = clock()
            val id = dao.createPlaylist(name, now)
            if (thenAdd != null) dao.addTrack(id, thenAdd, now)
            onDone(name)
        }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch { dao.renamePlaylist(playlistId, name, clock()) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch { dao.deletePlaylist(playlistId) }
    }

    fun addTrack(playlist: PlaylistSummary, track: Track, onDone: (name: String) -> Unit = {}) {
        viewModelScope.launch {
            dao.addTrack(playlist.id, track, clock())
            onDone(playlist.name)
        }
    }

    fun removeEntry(playlistId: Long, entryId: Long) {
        viewModelScope.launch { dao.removeEntry(playlistId, entryId, clock()) }
    }

    fun moveEntry(playlistId: Long, from: Int, to: Int) {
        viewModelScope.launch { dao.moveEntry(playlistId, from, to, clock()) }
    }

    companion object {
        /** [library] is the library state, so entries can be matched to files on the device. */
        fun factory(library: Flow<LibraryUiState>): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlaylistsViewModel(VeyraDatabase.get(this[APPLICATION_KEY]!!).playlistDao(), library)
            }
        }
    }
}
