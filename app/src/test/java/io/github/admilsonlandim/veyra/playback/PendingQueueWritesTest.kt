package io.github.admilsonlandim.veyra.playback

import io.github.admilsonlandim.veyra.data.media.TrackKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class PendingQueueWritesTest {

    private fun key(id: Long) = TrackKey(id, "Music/", "song$id.mp3", 1_000 + id, 180_000)

    private data class Write(val keys: List<TrackKey>?, val position: Pair<Int, Long>?)

    @Test
    fun aBurst_isWrittenOnce_withTheNewestValues() = runTest(StandardTestDispatcher()) {
        val written = mutableListOf<Write>()
        val writes = PendingQueueWrites(TestScope(testScheduler)) { k, p -> written += Write(k, p) }
        writes.offer(listOf(key(1)), 0 to 0L)
        writes.offer(null, 0 to 1_000L)
        writes.offer(listOf(key(1), key(2)), null)
        writes.offer(null, 1 to 2_000L)
        advanceUntilIdle()
        assertEquals(listOf(Write(listOf(key(1), key(2)), 1 to 2_000L)), written)
    }

    @Test
    fun close_stillWritesWhatIsPending_thenTheWriterEnds() = runTest(StandardTestDispatcher()) {
        val written = mutableListOf<Write>()
        val scope = TestScope(testScheduler)
        val writes = PendingQueueWrites(scope) { k, p -> written += Write(k, p) }
        writes.offer(null, 3 to 42_000L)
        writes.close()
        writes.offer(null, 9 to 9L) // after close: ignored, the service is gone
        advanceUntilIdle()
        assertEquals(listOf(Write(null, 3 to 42_000L)), written)
        assertTrue(scope.coroutineContext[Job]!!.children.none { it.isActive })
    }

    @Test
    fun offersFromManyThreads_whileASlowWriteRuns_endWithTheLastValue() = runBlocking {
        val job = Job()
        val written = Collections.synchronizedList(mutableListOf<Write>())
        val writes = PendingQueueWrites(CoroutineScope(Dispatchers.Default + job)) { k, p ->
            delay(1)
            written += Write(k, p)
        }
        (0 until 8).map { thread ->
            launch(Dispatchers.Default) {
                repeat(100) { writes.offer(listOf(key(thread.toLong())), thread to it.toLong()) }
            }
        }.joinAll()
        writes.offer(listOf(key(99)), 99 to 99L) // the newest of all
        writes.close()
        job.children.toList().joinAll()
        assertEquals(Write(listOf(key(99)), 99 to 99L), written.last())
        assertTrue(written.size < 801) // coalesced, not one write per offer
    }
}
