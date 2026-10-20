package io.github.admilsonlandim.veyra.data.queue

import io.github.admilsonlandim.veyra.data.media.TrackKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedQueueStoreTest {

    private fun key(id: Long) = TrackKey(id, "Music/", "song$id.mp3", 1_000 + id, 180_000)

    /** Two separate stores (like Room + DataStore) that suspend mid-write, so unserialized access interleaves. */
    private class SlowStorage(keys: List<TrackKey>, position: Pair<Int, Long>) {
        @Volatile var keys = keys
        @Volatile var position = position

        fun store() = SavedQueueStore(
            readKeys = { yield(); keys },
            writeKeys = { new -> yield(); keys = new },
            readPosition = { yield(); position },
            writePosition = { index, ms -> yield(); position = index to ms },
        )
    }

    @Test
    fun concurrentUpdates_neverLoseOneAnother() = runBlocking(Dispatchers.Default) {
        val all = (1L..60L).map(::key)
        val storage = SlowStorage(all, 0 to 0L)
        val store = storage.store()
        // Each removes a different track: without serialization, a read-modify-write based on an
        // old read would put back tracks another one had removed.
        (1L..60L step 2).map { id -> async { store.update { saved -> saved.copy(keys = saved.keys.filterNot { it.mediaStoreId == id }) } } }
            .awaitAll()
        assertEquals((2L..60L step 2).toList(), storage.keys.map { it.mediaStoreId })
    }

    @Test
    fun saves_andUpdates_interleaved_leaveItemsAndPositionConsistent() = runBlocking(Dispatchers.Default) {
        val storage = SlowStorage(listOf(key(1), key(2), key(3)), 2 to 5_000L)
        val store = storage.store()
        repeat(50) { round ->
            listOf(
                async { store.save(listOf(key(1), key(2), key(3)), 2 to round.toLong()) },
                async { store.update { saved -> SavedQueue(saved.keys.filterNot { it.mediaStoreId == 2L }, 1, saved.positionMs) } },
            ).awaitAll()
            val saved = store.read()
            // Whatever ran last, the position always belongs to the items written with it.
            val current = saved.keys.getOrNull(saved.currentIndex)
            assertEquals(3L, current?.mediaStoreId)
        }
    }

    @Test
    fun update_returningTheSameInstance_writesNothing() = runBlocking {
        var writes = 0
        val store = SavedQueueStore(
            readKeys = { listOf(key(1)) },
            writeKeys = { writes++ },
            readPosition = { 0 to 0L },
            writePosition = { _, _ -> writes++ },
        )
        store.update { it }
        assertEquals(0, writes)
        store.update { it.copy(positionMs = 10) }
        assertTrue(writes == 2)
    }
}
