package io.github.admilsonlandim.veyra.data.media

import kotlin.math.abs

/**
 * Stable reference to an audio file, stored by playlists (and, from Phase 7, by per-track
 * settings) instead of the bare MediaStore `_ID`.
 *
 * The `_ID` can change when MediaStore re-indexes, when a backup is restored or when an SD card
 * is swapped, so the key also keeps enough of the file's identity to find it again:
 * its folder and file name, its size and its duration.
 *
 * See [TrackKeyResolver] for how a key is matched back to a [Track], and its limitations.
 */
data class TrackKey(
    val mediaStoreId: Long,
    /** Folder relative to the storage root, e.g. "Music/Album/". Null if it could not be determined. */
    val relativePath: String?,
    val displayName: String?,
    val sizeBytes: Long,
    val durationMs: Long,
)

fun Track.toTrackKey(): TrackKey = TrackKey(
    mediaStoreId = id,
    relativePath = relativePath,
    displayName = displayName,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
)

/**
 * Finds the current [Track] for a [TrackKey], trying the most reliable evidence first:
 *
 * 1. Same `_ID`, and the file is consistent with the key (same name or same size). An id
 *    reused by MediaStore for a different file is rejected.
 * 2. Same folder and file name: re-indexed or restored (new id), or tags edited (new size).
 * 3. Same file name and size, if exactly one track matches: the file was moved.
 * 4. Same file name and a duration within [DURATION_TOLERANCE_MS], if exactly one track
 *    matches: moved and re-tagged.
 *
 * Anything else, including ambiguous matches, resolves to null ("unavailable"): playing the
 * wrong song is worse than skipping one.
 *
 * Limitations: a file both renamed and moved under a new id is lost; without a known folder
 * step 2 cannot help; identical copies of a file in several folders can only be told apart by
 * their path.
 *
 * Build one resolver per library snapshot: construction indexes the library once, after which
 * each lookup is constant time.
 */
class TrackKeyResolver(library: List<Track>) {

    private val byId = library.associateBy { it.id }
    private val byLocation = library
        .filter { it.relativePath != null && it.displayName != null }
        .groupBy { Location(it.relativePath, it.displayName) }
    private val byNameAndSize = library
        .filter { it.displayName != null }
        .groupBy { NameAndSize(it.displayName, it.sizeBytes) }
    private val byName = library
        .filter { it.displayName != null }
        .groupBy { it.displayName }

    fun resolve(key: TrackKey): Track? {
        byId[key.mediaStoreId]?.let { if (isSameFile(key, it)) return it }

        if (key.relativePath != null && key.displayName != null) {
            byLocation[Location(key.relativePath, key.displayName)]?.singleOrNull()?.let { return it }
        }
        if (key.displayName == null) return null

        byNameAndSize[NameAndSize(key.displayName, key.sizeBytes)]?.singleOrNull()?.let { return it }

        return byName[key.displayName]
            ?.filter { abs(it.durationMs - key.durationMs) <= DURATION_TOLERANCE_MS }
            ?.singleOrNull()
    }

    private fun isSameFile(key: TrackKey, track: Track): Boolean =
        (key.displayName != null && key.displayName == track.displayName) || key.sizeBytes == track.sizeBytes

    private data class Location(val relativePath: String?, val displayName: String?)
    private data class NameAndSize(val displayName: String?, val sizeBytes: Long)

    companion object {
        /** Re-encoding or re-tagging can shift the reported duration slightly. */
        const val DURATION_TOLERANCE_MS = 2_000L
    }
}
