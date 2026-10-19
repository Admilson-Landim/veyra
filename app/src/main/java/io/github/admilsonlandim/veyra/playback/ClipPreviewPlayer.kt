package io.github.admilsonlandim.veyra.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Small local player for the clip editor: plays a short excerpt of a file (e.g. the first
 * seconds after the start, or the last seconds before the end) without touching the main queue.
 *
 * It requests audio focus like the main player, so starting a preview pauses the music.
 * Main thread only; call [release] when the editor closes.
 */
class ClipPreviewPlayer(context: Context) {

    private val player = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .build()

    // Start of the excerpt in the file: the player's position is relative to it.
    private var excerptStartMs = 0L

    /** True while an excerpt is playing (observable from Compose). */
    var isPlaying by mutableStateOf(false)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@ClipPreviewPlayer.isPlaying = isPlaying
            }
        })
    }

    /** Plays [contentUri] from [fromMs] to [toMs] (absolute file positions), then stops. */
    fun play(contentUri: String, fromMs: Long, toMs: Long) {
        excerptStartMs = fromMs
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(contentUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(fromMs)
                        .setEndPositionMs(toMs)
                        .build(),
                )
                .build(),
        )
        player.prepare()
        player.play()
    }

    fun stop() {
        player.pause()
    }

    /** Absolute position in the file of the last excerpt played, or null if nothing played yet. */
    fun absolutePositionMs(): Long? =
        if (player.currentMediaItem == null) null else excerptStartMs + player.currentPosition

    fun release() {
        player.release()
    }
}
