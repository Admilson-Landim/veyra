package com.example.veyra.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateTest {

    @Test
    fun repeatMode_cyclesOffAllOne() {
        assertEquals(RepeatMode.All, RepeatMode.Off.next())
        assertEquals(RepeatMode.One, RepeatMode.All.next())
        assertEquals(RepeatMode.Off, RepeatMode.One.next())
    }

    @Test
    fun repeatMode_roundTripsWithPlayerConstants() {
        RepeatMode.entries.forEach { assertEquals(it, repeatModeFromPlayer(it.toPlayerRepeatMode())) }
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.Off.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.All.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.One.toPlayerRepeatMode())
    }

    @Test
    fun duration_unknownBecomesZero() {
        assertEquals(0L, sanitizeDuration(C.TIME_UNSET))
        assertEquals(0L, sanitizeDuration(-1))
        assertEquals(215_000L, sanitizeDuration(215_000))
    }

    @Test
    fun pauseButton_shownWhilePlayingOrBuffering() {
        assertTrue(shouldShowPause(playWhenReady = true, playbackState = Player.STATE_READY))
        assertTrue(shouldShowPause(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun playButton_shownWhenPausedIdleOrEnded() {
        assertFalse(shouldShowPause(playWhenReady = false, playbackState = Player.STATE_READY))
        // After an error the player is idle with playWhenReady still true: offer "play" to retry.
        assertFalse(shouldShowPause(playWhenReady = true, playbackState = Player.STATE_IDLE))
        assertFalse(shouldShowPause(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }

    @Test
    fun progressFraction_isClampedAndSafe() {
        assertEquals(0f, progressFraction(10_000, 0), 0f)
        assertEquals(0.5f, progressFraction(50_000, 100_000), 0.0001f)
        assertEquals(1f, progressFraction(120_000, 100_000), 0f)
        assertEquals(0f, progressFraction(-5, 100_000), 0f)
    }

    @Test
    fun hasMedia_dependsOnCurrentItem() {
        assertFalse(PlayerState().hasMedia)
        assertTrue(PlayerState(currentMediaId = "42").hasMedia)
    }
}
