package io.github.admilsonlandim.veyra.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * How the UI and the service talk about the sleep timer: the UI sends these custom commands,
 * the service publishes the state in the session extras (read by every connected controller).
 */
object SleepTimerSession {
    const val ACTION_START = "io.github.admilsonlandim.veyra.SLEEP_START"
    const val ACTION_EXTEND = "io.github.admilsonlandim.veyra.SLEEP_EXTEND"
    const val ACTION_CANCEL = "io.github.admilsonlandim.veyra.SLEEP_CANCEL"

    /** ACTION_START / ACTION_EXTEND argument; for ACTION_START, absent means "end of track". */
    const val ARG_MINUTES = "minutes"

    private const val EXTRA_ENDS_AT = "io.github.admilsonlandim.veyra.SLEEP_ENDS_AT"
    private const val EXTRA_END_OF_TRACK = "io.github.admilsonlandim.veyra.SLEEP_END_OF_TRACK"

    val commands: List<SessionCommand> =
        listOf(ACTION_START, ACTION_EXTEND, ACTION_CANCEL).map { SessionCommand(it, Bundle.EMPTY) }

    fun toExtras(state: SleepTimerState?): Bundle = Bundle().apply {
        when (state) {
            is SleepTimerState.At -> putLong(EXTRA_ENDS_AT, state.endsAtMs)
            SleepTimerState.EndOfTrack -> putBoolean(EXTRA_END_OF_TRACK, true)
            null -> Unit
        }
    }

    fun fromExtras(extras: Bundle): SleepTimerState? = when {
        extras.getBoolean(EXTRA_END_OF_TRACK) -> SleepTimerState.EndOfTrack
        extras.containsKey(EXTRA_ENDS_AT) -> SleepTimerState.At(extras.getLong(EXTRA_ENDS_AT))
        else -> null
    }
}
