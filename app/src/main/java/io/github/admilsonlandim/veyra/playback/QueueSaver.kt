package io.github.admilsonlandim.veyra.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.admilsonlandim.veyra.data.media.TrackKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Keeps the last queue saved while the service runs, so the app can resume it after it is closed
 * or killed. The player is read on the main thread; the writes go through [PendingQueueWrites],
 * off the main thread and in order.
 *
 * - The queue items are rewritten when the playlist changes.
 * - The current item and position are written when the item changes, when playback pauses or
 *   stops, every [PERIODIC_SAVE_MS] while playing, when the app is swiped away from Recents
 *   ([saveNow]) and when the service is destroyed ([stop]).
 *
 * [stop] never blocks the main thread: the last values are handed to [writes], whose writer runs
 * in a scope that outlives the service and ends by itself once they are written.
 *
 * @param keyOf the [TrackKey] of a queue item (see [TrackKeyRegistry]), or null if unknown.
 */
class QueueSaver(
    private val player: Player,
    private val keyOf: (MediaItem) -> TrackKey?,
    private val writes: PendingQueueWrites,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val periodicSave = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, PERIODIC_SAVE_MS)
        }
    }

    fun start() {
        player.addListener(this)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePosition()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(periodicSave)
        if (isPlaying) handler.postDelayed(periodicSave, PERIODIC_SAVE_MS) else savePosition()
    }

    /** Saves the current position right away (e.g. the app was swiped away from Recents). */
    fun saveNow() = savePosition()

    /** Final save when the service goes away; returns at once, the write finishes in the background. */
    fun stop() {
        handler.removeCallbacks(periodicSave)
        player.removeListener(this)
        savePosition()
        writes.close()
    }

    private fun saveQueue() {
        // An empty queue is left as it was: a cleared player should not erase what can be resumed.
        if (player.mediaItemCount == 0) return
        val keys = (0 until player.mediaItemCount).mapNotNull { keyOf(player.getMediaItemAt(it)) }
        if (keys.size != player.mediaItemCount) return
        writes.offer(keys, positionSnapshot())
    }

    private fun savePosition() {
        positionSnapshot()?.let { writes.offer(null, it) }
    }

    /** Current item index and position in the file (clip start + position in the clip). */
    private fun positionSnapshot(): Pair<Int, Long>? {
        val item = player.currentMediaItem ?: return null
        val inFile = item.clippingConfiguration.startPositionMs + player.currentPosition.coerceAtLeast(0)
        return player.currentMediaItemIndex to inFile
    }

    private companion object {
        const val PERIODIC_SAVE_MS = 15_000L
    }
}

/**
 * Hands queue saves from the main thread to a single writer coroutine in [scope]. Only the newest
 * value of each part (items, position) waits to be written, so a burst of changes costs one write
 * and an older value can never be written after a newer one. After [close], what is pending is
 * still written, then the writer ends.
 */
class PendingQueueWrites(
    scope: CoroutineScope,
    private val write: suspend (keys: List<TrackKey>?, position: Pair<Int, Long>?) -> Unit,
) {
    private val lock = Any()
    private var keys: List<TrackKey>? = null
    private var position: Pair<Int, Long>? = null
    private var closed = false
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in wakeUp) {
                val (pendingKeys, pendingPosition) = take()
                if (pendingKeys == null && pendingPosition == null) continue
                runCatching { write(pendingKeys, pendingPosition) }
                    .onFailure { Log.w(TAG, "Couldn't save the queue", it) }
            }
        }
    }

    /** Replaces what is waiting to be written; null leaves that part as it is. Any thread. */
    fun offer(keys: List<TrackKey>?, position: Pair<Int, Long>?) {
        synchronized(lock) {
            if (closed) return
            if (keys != null) this.keys = keys
            if (position != null) this.position = position
        }
        wakeUp.trySend(Unit)
    }

    /** No more values: the writer saves what is pending, then ends. */
    fun close() {
        synchronized(lock) { closed = true }
        wakeUp.close()
    }

    private fun take(): Pair<List<TrackKey>?, Pair<Int, Long>?> = synchronized(lock) {
        (keys to position).also {
            keys = null
            position = null
        }
    }

    private companion object {
        const val TAG = "QueueSaver"
    }
}
