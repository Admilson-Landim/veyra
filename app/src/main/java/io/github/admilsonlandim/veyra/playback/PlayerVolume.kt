package io.github.admilsonlandim.veyra.playback

import androidx.media3.common.Player
import io.github.admilsonlandim.veyra.data.audio.finalVolume

/**
 * Owns `player.volume`; nothing else writes it. Balanced volume (ReplayGain) and the sleep
 * timer's fade each set their factor here, and the product is applied, so they never fight.
 * The system volume and the equalizer are separate and untouched.
 */
class PlayerVolume(private val player: Player) {

    private var replayGain = 1f
    private var fade = 1f

    fun setReplayGain(factor: Float) {
        replayGain = factor
        apply()
    }

    fun setFade(factor: Float) {
        fade = factor
        apply()
    }

    private fun apply() {
        player.volume = finalVolume(replayGain, fade)
    }
}
