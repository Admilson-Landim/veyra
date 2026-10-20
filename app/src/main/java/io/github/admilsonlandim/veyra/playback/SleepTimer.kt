package io.github.admilsonlandim.veyra.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The armed sleep timer, as the UI shows it. Not saved: it ends with the service. */
sealed interface SleepTimerState {
    /** Pauses at [endsAtMs] on the elapsed-realtime clock (unaffected by wall-clock changes). */
    data class At(val endsAtMs: Long) : SleepTimerState

    /** Pauses when the current item ends. */
    data object EndOfTrack : SleepTimerState
}

/**
 * The volume fade's duration: long enough to drift off without an abrupt stop, short enough not
 * to lose much of the music. The fade starts this long before the timer ends.
 */
const val SLEEP_FADE_MS = 20_000L

/** Fade factor for [remainingMs] left: 1 (full volume) until the fade starts, then down to 0. */
fun sleepFade(remainingMs: Long, fadeMs: Long = SLEEP_FADE_MS): Float =
    (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)

/** What the timer needs from the player; an interface so the logic runs in JVM tests. */
interface SleepTimerPlayer {
    fun pause()

    /** ExoPlayer's pause-at-end-of-item, which stops exactly when the current item ends. */
    fun setPauseAtEndOfItem(enabled: Boolean)

    /** Time left in the current item, or null if unknown (nothing loaded, live, preparing). */
    fun remainingInItemMs(): Long?

    /** The fade factor; [PlayerVolume] combines it with the rest of the volume. */
    fun setFade(fade: Float)
}

/**
 * Sleep timer, run by the playback service so it works with the screen off and the app in the
 * background (the service is in the foreground while playing, and ExoPlayer's wake lock keeps
 * the CPU running).
 *
 * Pausing by hand does not cancel it: "stop in 30 minutes" is about time, and a pause for a call
 * should not lose it. If it ends while paused, it simply clears. On ending it pauses (never
 * stops the service: the idle rules of Phase 4 then apply) and restores the volume.
 *
 * @param now elapsed-realtime milliseconds (SystemClock.elapsedRealtime in the app).
 */
class SleepTimer(
    private val player: SleepTimerPlayer,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onStateChanged: (SleepTimerState?) -> Unit,
) {
    var state: SleepTimerState? = null
        private set(value) {
            field = value
            onStateChanged(value)
        }

    private var job: Job? = null

    fun startMinutes(minutes: Int) {
        reset()
        state = SleepTimerState.At(now() + minutes * 60_000L)
        job = scope.launch { runUntilDeadline() }
    }

    fun startEndOfTrack() {
        reset()
        player.setPauseAtEndOfItem(true)
        state = SleepTimerState.EndOfTrack
        job = scope.launch { fadeTowardsItemEnd() }
    }

    /** Adds [minutes] to a running timer, counting from now if it was about to end. */
    fun extend(minutes: Int) {
        val at = state as? SleepTimerState.At ?: return
        job?.cancel()
        player.setFade(1f)
        state = SleepTimerState.At(maxOf(at.endsAtMs, now()) + minutes * 60_000L)
        job = scope.launch { runUntilDeadline() }
    }

    fun cancel() {
        reset()
        state = null
    }

    /** The player paused itself at the end of the item (end-of-track mode). */
    fun onPausedAtEndOfItem() {
        if (state == SleepTimerState.EndOfTrack) finish()
    }

    private suspend fun runUntilDeadline() {
        while (scope.isActive) {
            val endsAt = (state as? SleepTimerState.At)?.endsAtMs ?: return
            val remaining = endsAt - now()
            if (remaining <= 0) {
                finish()
                return
            }
            player.setFade(sleepFade(remaining))
            // Sleep until the fade starts, then step it every 100 ms.
            delay(if (remaining > SLEEP_FADE_MS) remaining - SLEEP_FADE_MS else FADE_STEP_MS)
        }
    }

    private suspend fun fadeTowardsItemEnd() {
        while (scope.isActive && state == SleepTimerState.EndOfTrack) {
            player.setFade(player.remainingInItemMs()?.let(::sleepFade) ?: 1f)
            delay(FADE_STEP_MS)
        }
    }

    private fun finish() {
        player.pause()
        reset()
        state = null
    }

    private fun reset() {
        job?.cancel()
        job = null
        player.setPauseAtEndOfItem(false)
        player.setFade(1f)
    }

    private companion object {
        const val FADE_STEP_MS = 100L
    }
}
