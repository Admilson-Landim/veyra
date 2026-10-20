package io.github.admilsonlandim.veyra.data.media

import io.github.admilsonlandim.veyra.data.db.PlaylistDao
import io.github.admilsonlandim.veyra.data.db.SavedQueueDao
import io.github.admilsonlandim.veyra.data.db.TrackSettingsDao
import io.github.admilsonlandim.veyra.data.queue.SavedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drops what the app stored about a removed track:
 *
 * - The saved queue (Phase 12), always: the player's own queue is cleared by the caller, and
 *   when it ends up empty the service keeps the old saved queue, which would still hold the track.
 * - Playlist entries and the start/end clip only when the file is deleted for good. A trashed
 *   file keeps its MediaStore id, so if the user restores it, its playlists and clip come back;
 *   until then those entries show as unavailable.
 *
 * Ringtone excerpts created by the app are separate files (possibly the current ringtone) and
 * do not depend on the original, so they stay.
 */
class TrackReferenceCleaner(
    private val playlists: PlaylistDao,
    private val settings: TrackSettingsDao,
    private val queue: SavedQueueDao,
    private val readQueuePosition: suspend () -> Pair<Int, Long>,
    private val writeQueuePosition: suspend (Int, Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun clean(track: Track, mode: RemovalMode) = withContext(Dispatchers.IO) {
        val (index, position) = readQueuePosition()
        val saved = SavedQueue(queue.items().map { it.key }, index, position)
        val updated = saved.without(track)
        if (updated !== saved) {
            queue.replace(updated.keys)
            writeQueuePosition(updated.currentIndex, updated.positionMs)
        }
        if (mode == RemovalMode.Delete) {
            playlists.removeEntriesWhere(now()) { it.key.refersTo(track) }
            val rows = settings.all().filter { it.toStoredClip().key.refersTo(track) }.map { it.id }
            if (rows.isNotEmpty()) settings.deleteByIds(rows)
        }
    }
}
