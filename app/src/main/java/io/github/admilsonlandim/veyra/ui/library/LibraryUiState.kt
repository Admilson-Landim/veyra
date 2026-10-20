package io.github.admilsonlandim.veyra.ui.library

import io.github.admilsonlandim.veyra.data.media.ArtistGroup
import io.github.admilsonlandim.veyra.data.media.FolderGroup
import io.github.admilsonlandim.veyra.data.media.LibraryView
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackSort
import kotlin.random.Random

/** Where the app stands with the audio read permission. */
enum class AudioPermission {
    /** Not checked yet (before the first resume). */
    Unknown,

    /** Not granted, and the user has not been asked in this session. */
    NotRequested,

    /** Refused, but the system will still show the dialog again. */
    Denied,

    /** Refused and the system will no longer show the dialog: only app settings can grant it. */
    PermanentlyDenied,
    Granted,
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** [permission] is NotRequested, Denied or PermanentlyDenied. */
    data class PermissionRequired(val permission: AudioPermission) : LibraryUiState

    /** No visible tracks; [allTracks] may still hold tracks hidden by the folder/duration filters. */
    data class Empty(val allTracks: List<Track> = emptyList()) : LibraryUiState

    /**
     * [tracks] is what the library shows, filtered and in [sort] order (also the play order).
     * [allTracks] is every music file, including excluded folders: playlists and per-track
     * settings still recognise those tracks.
     * [folders] and [artists] group [tracks], each group keeping their order.
     */
    data class Content(
        val tracks: List<Track>,
        val sort: TrackSort = TrackSort.Title,
        val allTracks: List<Track> = tracks,
        val view: LibraryView = LibraryView.Tracks,
        val folders: List<FolderGroup> = emptyList(),
        val artists: List<ArtistGroup> = emptyList(),
    ) : LibraryUiState

    data object Error : LibraryUiState
}

/** All the music files known to the device, whatever the library filters, or null if not loaded. */
val LibraryUiState.allTracksOrNull: List<Track>?
    get() = when (this) {
        is LibraryUiState.Content -> allTracks
        is LibraryUiState.Empty -> allTracks
        else -> null
    }

/** A queue to start: [tracks] from [startIndex], with the player's shuffle mode set to [shuffle]. */
data class QueueRequest(val tracks: List<Track>, val startIndex: Int, val shuffle: Boolean)

/** "Play all": [tracks] in their order, from the first; null when there is nothing to play. */
fun playAllRequest(tracks: List<Track>): QueueRequest? =
    tracks.takeIf { it.isNotEmpty() }?.let { QueueRequest(it, startIndex = 0, shuffle = false) }

/** "Shuffle": [tracks] with the player's shuffle on, starting from a random one. */
fun shuffleRequest(tracks: List<Track>, random: Random = Random.Default): QueueRequest? =
    tracks.takeIf { it.isNotEmpty() }?.let { QueueRequest(it, startIndex = random.nextInt(it.size), shuffle = true) }

/** What a search found, in the current Library order. Folders and artists are matched by name. */
data class SearchResults(
    val query: String,
    val tracks: List<Track>,
    val folders: List<FolderGroup>,
    val artists: List<ArtistGroup>,
)
