package com.example.veyra.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the ExoPlayer and exposes it through a MediaSession. The UI never touches the player
 * directly: it connects with a MediaController (see PlayerConnection).
 *
 * Lifecycle: the system creates the service when the first controller binds. Media3 promotes it
 * to a foreground service while playing and lets it stop once playback ends and no controller
 * is bound. onDestroy releases the player and the session.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val errorSkipPolicy = ErrorSkipPolicy()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(SkipBrokenItems(player))
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
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
}
