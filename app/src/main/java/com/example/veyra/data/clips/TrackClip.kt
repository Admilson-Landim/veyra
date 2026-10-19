package com.example.veyra.data.clips

import com.example.veyra.data.media.Track
import com.example.veyra.data.media.TrackKey
import com.example.veyra.data.media.TrackKeyResolver

/**
 * Shortest allowed clip. Anything shorter is barely audible and almost certainly a mistake,
 * and with repeat on, very short clips would make the player skip through tracks in bursts.
 * It also matches the 1 s fine-adjust step of the editor.
 */
const val MIN_CLIP_LENGTH_MS = 1_000L

/** A per-track start/end. [endMs] null means "until the natural end of the file". */
data class TrackClip(val startMs: Long, val endMs: Long?) {
    /** Start at 0 and no end is the default: nothing to store or apply. */
    val isDefault: Boolean get() = startMs == 0L && endMs == null
}

sealed interface ClipValidation {
    data object Valid : ClipValidation
    data object NegativeStart : ClipValidation
    data object StartNotBeforeEnd : ClipValidation
    data object TooShort : ClipValidation
    data object EndBeyondDuration : ClipValidation
    data object UnknownDuration : ClipValidation
}

/** Checks `0 <= start < end <= duration` with at least [MIN_CLIP_LENGTH_MS] between start and end. */
fun validateClip(startMs: Long, endMs: Long?, durationMs: Long): ClipValidation {
    if (durationMs <= 0) return ClipValidation.UnknownDuration
    if (startMs < 0) return ClipValidation.NegativeStart
    if (endMs != null && endMs > durationMs) return ClipValidation.EndBeyondDuration
    val end = endMs ?: durationMs
    if (startMs >= end) return ClipValidation.StartNotBeforeEnd
    if (end - startMs < MIN_CLIP_LENGTH_MS) return ClipValidation.TooShort
    return ClipValidation.Valid
}

fun TrackClip.validateFor(durationMs: Long): ClipValidation = validateClip(startMs, endMs, durationMs)

/**
 * The clip to apply when playing a track of [durationMs], or null to play the whole file:
 * no clip, the default clip, or a clip that no longer fits the file (e.g. the file was replaced
 * by a shorter one) are all played in full.
 */
fun clipToApply(clip: TrackClip?, durationMs: Long): TrackClip? =
    clip?.takeIf { !it.isDefault && it.validateFor(durationMs) == ClipValidation.Valid }

/** A stored clip, as read from the database. */
data class StoredClip(val rowId: Long, val key: TrackKey, val clip: TrackClip, val updatedAt: Long)

/** A stored clip matched to a library track. [isValid] is false when it no longer fits the file. */
data class ResolvedClip(val rowIds: List<Long>, val clip: TrackClip, val isValid: Boolean)

/**
 * Matches stored clips to library tracks with [TrackKeyResolver]. If several rows point to the
 * same track (e.g. saved before and after a re-index), the most recently updated one wins; all
 * their row ids are kept so saving or resetting can replace them together.
 */
fun resolveClips(stored: List<StoredClip>, library: List<Track>): Map<Long, ResolvedClip> {
    if (stored.isEmpty() || library.isEmpty()) return emptyMap()
    val resolver = TrackKeyResolver(library)
    return stored
        .mapNotNull { row -> resolver.resolve(row.key)?.let { track -> track to row } }
        .groupBy({ it.first }, { it.second })
        .map { (track, rows) ->
            val latest = rows.maxBy { it.updatedAt }
            track.id to ResolvedClip(
                rowIds = rows.map { it.rowId },
                clip = latest.clip,
                isValid = latest.clip.validateFor(track.durationMs) == ClipValidation.Valid,
            )
        }
        .toMap()
}
