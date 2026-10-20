package io.github.admilsonlandim.veyra.data.media

import io.github.admilsonlandim.veyra.data.queue.SavedQueue

/**
 * True when this stored key points at [track], the file being removed: the same MediaStore id
 * still describing the same file (name or size, as in [TrackKeyResolver]), or the same folder and
 * file name (the id changed after a re-index).
 */
fun TrackKey.refersTo(track: Track): Boolean =
    (mediaStoreId == track.id && (displayName == track.displayName || sizeBytes == track.sizeBytes)) ||
        (relativePath != null && displayName != null && relativePath == track.relativePath && displayName == track.displayName)

/**
 * The saved queue without [track]. The current item keeps its place among the remaining tracks;
 * when it is the removed track, the next one becomes current, from its start (the last one if
 * it was at the end). Returns the same instance when the track is not in the queue.
 */
fun SavedQueue.without(track: Track): SavedQueue {
    val keep = keys.map { !it.refersTo(track) }
    if (keep.all { it }) return this
    val remaining = keys.filterIndexed { index, _ -> keep[index] }
    val current = currentIndex.coerceIn(0, keys.lastIndex)
    val removedBefore = (0 until current).count { !keep[it] }
    val index = (current - removedBefore).coerceIn(0, maxOf(remaining.lastIndex, 0))
    return SavedQueue(remaining, index, if (keep[current]) positionMs else 0)
}
