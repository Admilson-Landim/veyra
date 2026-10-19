package io.github.admilsonlandim.veyra.data.queue

import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackKey
import io.github.admilsonlandim.veyra.data.media.TrackKeyResolver

/**
 * The last queue, as saved by the playback service: tracks as [TrackKey]s (so they survive a
 * MediaStore re-index), the current item and the position in that file.
 *
 * @param positionMs position in the file, not in the clipped range, so it stays meaningful if the
 *   track's start/end changes before the queue is restored.
 */
data class SavedQueue(val keys: List<TrackKey>, val currentIndex: Int, val positionMs: Long)

/** A queue ready to load into the player. [startPositionMs] is a position in the file. */
data class QueueRestore(val tracks: List<Track>, val startIndex: Int, val startPositionMs: Long)

/**
 * Matches a saved queue to the current library. Tracks that are gone are dropped. If the current
 * track itself is gone, playback is set to start at the next track that still exists (or the
 * last one before it), from its beginning. Returns null when nothing can be restored.
 */
fun planRestore(saved: SavedQueue, library: List<Track>): QueueRestore? {
    if (saved.keys.isEmpty() || library.isEmpty()) return null
    val resolver = TrackKeyResolver(library)
    val resolved = saved.keys.map(resolver::resolve)
    val tracks = resolved.filterNotNull()
    if (tracks.isEmpty()) return null

    val current = saved.currentIndex.coerceIn(0, resolved.lastIndex)
    // Index of the current item among the tracks that are still available.
    val survivorsBefore = resolved.subList(0, current).count { it != null }
    return if (resolved[current] != null) {
        QueueRestore(tracks, survivorsBefore, saved.positionMs.coerceAtLeast(0))
    } else {
        QueueRestore(tracks, survivorsBefore.coerceAtMost(tracks.lastIndex), 0)
    }
}

/**
 * Converts a position in the file into a position within a clip starting at [clipStartMs] and
 * ending at [clipEndMs] (exclusive). Outside the clip, playback starts at the clip's beginning.
 */
fun positionInClip(filePositionMs: Long, clipStartMs: Long, clipEndMs: Long): Long =
    if (filePositionMs in clipStartMs until clipEndMs) filePositionMs - clipStartMs else 0
