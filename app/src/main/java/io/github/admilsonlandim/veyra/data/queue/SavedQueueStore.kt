package io.github.admilsonlandim.veyra.data.queue

import android.content.Context
import io.github.admilsonlandim.veyra.data.db.VeyraDatabase
import io.github.admilsonlandim.veyra.data.media.TrackKey
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only way to read or write the saved queue: its items (Room) and current item and position
 * (preferences) are two stores, so every access goes through one [Mutex]. A read-modify-write
 * ([update], e.g. dropping a removed track) can then never interleave with the playback service's
 * saves, and a write never lands half-way through another. The mutex is fair: writes happen in
 * the order they were asked for.
 */
class SavedQueueStore(
    private val readKeys: suspend () -> List<TrackKey>,
    private val writeKeys: suspend (List<TrackKey>) -> Unit,
    private val readPosition: suspend () -> Pair<Int, Long>,
    private val writePosition: suspend (index: Int, positionMs: Long) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun read(): SavedQueue = mutex.withLock { readUnlocked() }

    /** Writes the items and/or the position; null leaves that part as it is. */
    suspend fun save(keys: List<TrackKey>?, position: Pair<Int, Long>?) = mutex.withLock {
        keys?.let { writeKeys(it) }
        position?.let { writePosition(it.first, it.second) }
    }

    /** Replaces the saved queue with [transform] of it, atomically; returning the same instance writes nothing. */
    suspend fun update(transform: (SavedQueue) -> SavedQueue) = mutex.withLock {
        val saved = readUnlocked()
        val updated = transform(saved)
        if (updated !== saved) {
            writeKeys(updated.keys)
            writePosition(updated.currentIndex, updated.positionMs)
        }
    }

    private suspend fun readUnlocked(): SavedQueue {
        val (index, position) = readPosition()
        return SavedQueue(readKeys(), index, position)
    }

    companion object {
        @Volatile
        private var instance: SavedQueueStore? = null

        /** One per process: the service and the screens must share the same lock. */
        fun get(context: Context): SavedQueueStore =
            instance ?: synchronized(this) {
                instance ?: run {
                    val dao = VeyraDatabase.get(context).savedQueueDao()
                    val preferences = AppPreferences.get(context)
                    SavedQueueStore(
                        readKeys = { dao.items().map { it.key } },
                        writeKeys = dao::replace,
                        readPosition = preferences::queuePosition,
                        writePosition = preferences::setQueuePosition,
                    )
                }.also { instance = it }
            }
    }
}
