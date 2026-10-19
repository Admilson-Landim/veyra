package io.github.admilsonlandim.veyra.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.admilsonlandim.veyra.data.db.SavedQueueDao
import io.github.admilsonlandim.veyra.data.media.TrackKey
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Keeps the last queue saved while the service runs, so the app can resume it after it is closed
 * or killed. The player is read on the main thread; database and preference writes go to IO,
 * one at a time and keeping only the latest pending value, so writes never land out of order.
 *
 * - The queue items are rewritten when the playlist changes.
 * - The current item and position are written when the item changes, when playback pauses or
 *   stops, every [PERIODIC_SAVE_MS] while playing, and when the service is destroyed.
 */
class QueueSaver(
    private val player: Player,
    private val dao: SavedQueueDao,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val periodicSave = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, PERIODIC_SAVE_MS)
        }
    }

    private val pendingQueue = MutableStateFlow<List<TrackKey>?>(null)
    private val pendingPosition = MutableStateFlow<Pair<Int, Long>?>(null)

    fun start() {
        player.addListener(this)
        scope.launch(Dispatchers.IO) { pendingQueue.filterNotNull().collect { dao.replace(it) } }
        scope.launch(Dispatchers.IO) {
            pendingPosition.filterNotNull().collect { preferences.setQueuePosition(it.first, it.second) }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePosition()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(periodicSave)
        if (isPlaying) handler.postDelayed(periodicSave, PERIODIC_SAVE_MS) else savePosition()
    }

    /** Final save when the service goes away; blocks briefly so the write is not lost. */
    fun stop() {
        handler.removeCallbacks(periodicSave)
        player.removeListener(this)
        val queue = pendingQueue.value
        val snapshot = positionSnapshot()
        runBlocking(Dispatchers.IO) {
            queue?.let { dao.replace(it) }
            snapshot?.let { preferences.setQueuePosition(it.first, it.second) }
        }
    }

    private fun saveQueue() {
        // An empty queue is left as it was: a cleared player should not erase what can be resumed.
        if (player.mediaItemCount == 0) return
        val keys = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).trackKey() }
        if (keys.size != player.mediaItemCount) return
        pendingQueue.value = keys
        savePosition()
    }

    private fun savePosition() {
        positionSnapshot()?.let { pendingPosition.value = it }
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
