package com.example.veyra.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.veyra.MainActivity

/**
 * Owns the ExoPlayer and exposes it through a MediaSession. The UI never touches the player
 * directly: it connects with a MediaController (see PlayerConnection).
 *
 * Lifecycle: the system creates the service when the first controller binds. Media3 promotes it
 * to a foreground service (with the media notification) while playing, and drops it out of the
 * foreground 10 minutes after playback pauses or ends. onDestroy releases the player and session.
 *
 * Swiping the app away from Recents uses Media3's default onTaskRemoved: if playback is not
 * ongoing the service stops; if music is playing it keeps going, controllable from the
 * notification and the lock screen, which is what a music player is expected to do.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val errorSkipPolicy = ErrorSkipPolicy()

    // Media3 leaves a paused service running (just not in the foreground). Stop it ourselves
    // after a long idle period; if the UI is still bound, it is destroyed once the UI unbinds.
    private val idleHandler = Handler(Looper.getMainLooper())
    private val stopWhenIdle = Runnable { stopSelf() }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Pause for calls and other apps' playback, duck for short sounds, resume after.
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            // Local files only: keep the CPU (not Wi-Fi) awake while playing with the screen off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(SkipBrokenItems(player))
        player.addListener(IdleStopTimer())
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayerIntent())
            .build()
        // Also covers a service restarted by the system with an empty player.
        scheduleIdleStop()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Media3 returns START_STICKY, so after the process is killed the system recreates the
     * service with an empty player while the old "playing" notification lingers. The queue is
     * not persisted, so there is nothing to resume: don't ask to be restarted.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(stopWhenIdle)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Tapping the notification (or the lock screen media card) opens the app on the player. */
    private fun openPlayerIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun scheduleIdleStop() {
        idleHandler.removeCallbacks(stopWhenIdle)
        idleHandler.postDelayed(stopWhenIdle, IDLE_STOP_DELAY_MS)
    }

    private inner class IdleStopTimer : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) idleHandler.removeCallbacks(stopWhenIdle) else scheduleIdleStop()
        }
    }

    /** On an unplayable item (deleted or unreadable file), moves on to the next one. */
    private inner class SkipBrokenItems(private val player: Player) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (errorSkipPolicy.shouldSkip(player.mediaItemCount, player.hasNextMediaItem())) {
                player.seekToNextMediaItem()
                // An error leaves the player idle; prepare again. playWhenReady is still true.
                player.prepare()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorSkipPolicy.reset()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) errorSkipPolicy.reset()
        }
    }

    private companion object {
        // Longer than Media3's 10-minute foreground timeout, so the notification has already
        // become dismissible when the service goes away.
        const val IDLE_STOP_DELAY_MS = 15 * 60 * 1000L
    }
}
