package com.example.veyra.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.clips.clipToApply
import com.example.veyra.data.media.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** A playback error reported by the session, with the title of the item that failed. */
data class PlaybackError(val title: String?)

/**
 * Connects to PlaybackService through a MediaController and turns its events into a
 * [StateFlow]. Must be used from the main thread, as MediaController requires.
 *
 * Call [connect] once and [release] when the owner goes away; commands sent before the
 * controller is connected are ignored.
 */
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<PlaybackError>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<PlaybackError> = _errors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            _errors.tryEmit(PlaybackError(controller?.mediaMetadata?.title?.toString()))
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                // Released before connecting (cancelled), or the connection failed.
                if (controllerFuture !== future || future.isCancelled) return@addListener
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                publish()
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller = null
        _state.value = PlayerState()
    }

    /** [state], with the position refreshed every [intervalMs] while audio is playing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateWithProgress(intervalMs: Long = 500): Flow<PlayerState> =
        state.flatMapLatest { snapshot ->
            if (!snapshot.isPlaying) {
                flowOf(snapshot)
            } else {
                flow {
                    while (true) {
                        emit(snapshot.copy(positionMs = controller?.currentPosition ?: snapshot.positionMs))
                        delay(intervalMs)
                    }
                }
            }
        }

    /**
     * Replaces the queue with [tracks] and starts playing at [startIndex], each with its clip.
     * [shuffle], when given, sets the player's shuffle mode for this queue ("Play all" turns it
     * off, "Shuffle" turns it on); null keeps the current mode.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null, clipFor: (Track) -> TrackClip?) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem(clipFor(it)) }, startIndex, 0L)
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    /** Restores saved shuffle/repeat modes. */
    fun setModes(shuffle: Boolean, repeat: RepeatMode) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.repeatMode = repeat.toPlayerRepeatMode()
    }

    /** Position in the file (not in the clipped range) of the current item, or null if none. */
    fun absolutePositionMs(): Long? {
        val c = controller ?: return null
        val item = c.currentMediaItem ?: return null
        return item.clippingConfiguration.startPositionMs + c.currentPosition
    }

    /**
     * Brings the clip of every queue item in line with [clipFor], replacing only items whose
     * clipping differs (so reconnecting to a playing queue does not re-prepare anything).
     * A changed current item keeps playing at the same point of the file when that point is
     * inside the new range, otherwise from the new start.
     *
     * Items are swapped by inserting the new one and removing the old, not with
     * replaceMediaItem: for the same URI ExoPlayer updates the existing source in place and the
     * new clipping is silently ignored (ProgressiveMediaSource.canUpdateMediaItem does not
     * compare clipping).
     *
     * @param trackFor the library track for a media id, or null if unknown (item left as is).
     */
    fun syncClips(trackFor: (mediaId: String) -> Track?, clipFor: (Track) -> TrackClip?) {
        val c = controller ?: return
        for (index in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(index)
            val track = trackFor(item.mediaId) ?: continue
            val wanted = clipToApply(clipFor(track), track.durationMs)
            val wantedStart = wanted?.startMs ?: 0L
            val wantedEnd = wanted?.endMs ?: C.TIME_END_OF_SOURCE
            val current = item.clippingConfiguration
            if (current.startPositionMs == wantedStart && current.endPositionMs == wantedEnd) continue

            val isCurrent = index == c.currentMediaItemIndex
            val absolute = current.startPositionMs + if (isCurrent) c.currentPosition else 0L
            c.addMediaItem(index + 1, track.toMediaItem(wanted))
            if (isCurrent) {
                // Move playback to the new copy before dropping the old one, so it never
                // falls through to the next track.
                val end = wanted?.endMs ?: track.durationMs
                val relative = if (absolute in wantedStart until end) absolute - wantedStart else 0L
                c.seekTo(index + 1, relative)
            }
            c.removeMediaItem(index)
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (shouldShowPause(c.playWhenReady, c.playbackState)) {
            c.pause()
        } else {
            // Recover from an error (idle) or restart after the queue ended.
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    /** Restarts the current track if it has played for a few seconds, otherwise goes back one. */
    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = repeatModeFromPlayer(c.repeatMode).next().toPlayerRepeatMode()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerState(
            isConnected = true,
            currentMediaId = c.currentMediaItem?.mediaId,
            title = c.mediaMetadata.title?.toString(),
            artist = c.mediaMetadata.artist?.toString(),
            artworkUri = c.currentMediaItem?.requestMetadata?.mediaUri?.toString(),
            isPlaying = c.isPlaying,
            showPause = shouldShowPause(c.playWhenReady, c.playbackState),
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = sanitizeDuration(c.duration),
            currentIndex = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            repeatMode = repeatModeFromPlayer(c.repeatMode),
            shuffleEnabled = c.shuffleModeEnabled,
        )
    }
}
