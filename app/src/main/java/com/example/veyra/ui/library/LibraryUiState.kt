package com.example.veyra.ui.library

import com.example.veyra.data.media.Track

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

    data object Empty : LibraryUiState

    data class Content(val tracks: List<Track>) : LibraryUiState

    data object Error : LibraryUiState
}
