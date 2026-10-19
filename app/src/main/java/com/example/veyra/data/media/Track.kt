package com.example.veyra.data.media

/**
 * An audio file indexed by the MediaStore. Pure Kotlin, so it can be used in JVM tests.
 *
 * Nullable text fields mean "unknown": the UI decides how to present that.
 */
data class Track(
    val id: Long,
    /** `content://` URI built from [id]; kept as a String so the model has no Android types. */
    val contentUri: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String?,
    /** Folder relative to the storage root (e.g. "Music/Album/"); derived from the file path before Android 10. */
    val relativePath: String?,
    val sizeBytes: Long,
    /** Last modification time, in seconds since the epoch (MediaStore's unit). */
    val dateModifiedSeconds: Long,
    /** When MediaStore first indexed the file, in seconds since the epoch. */
    val dateAddedSeconds: Long = 0,
)
