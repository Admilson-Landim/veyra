package com.example.veyra.playback

import androidx.media3.common.C
import androidx.media3.common.Player

enum class RepeatMode {
    Off, All, One;

    /** Order used by the repeat button: off → all → one → off. */
    fun next(): RepeatMode = when (this) {
        Off -> All
        All -> One
        One -> Off
    }
}

/** Snapshot of the session player, as the UI needs it. */
data class PlayerState(
    val isConnected: Boolean = false,
    /** MediaItem id of the current item, i.e. the Track id as a String. Null when the queue is empty. */
    val currentMediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    /** True only while audio is actually advancing (drives the position ticker). */
    val isPlaying: Boolean = false,
    /** True when the play/pause button should offer "pause" (also while buffering). */
    val showPause: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleEnabled: Boolean = false,
) {
    val hasMedia: Boolean get() = currentMediaId != null
}

fun repeatModeFromPlayer(@Player.RepeatMode mode: Int): RepeatMode = when (mode) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}

@Player.RepeatMode
fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.One -> Player.REPEAT_MODE_ONE
}

/** The player reports C.TIME_UNSET (a negative value) until the duration is known. */
fun sanitizeDuration(durationMs: Long): Long =
    if (durationMs == C.TIME_UNSET || durationMs < 0) 0 else durationMs

/** "Pause" while the player intends to play, even if it is still buffering. */
fun shouldShowPause(playWhenReady: Boolean, @Player.State playbackState: Int): Boolean =
    playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

/** Progress in 0..1; 0 while the duration is unknown. */
fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
