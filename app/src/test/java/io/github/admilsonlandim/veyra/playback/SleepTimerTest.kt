package io.github.admilsonlandim.veyra.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private class FakePlayer : SleepTimerPlayer {
        var paused = 0
        var pauseAtEnd = false
        var remaining: Long? = null
        var lastFade = 1f
        override fun pause() { paused++ }
        override fun setPauseAtEndOfItem(enabled: Boolean) { pauseAtEnd = enabled }
        override fun remainingInItemMs(): Long? = remaining
        override fun setFade(fade: Float) { lastFade = fade }
    }

    private val player = FakePlayer()
    private val states = mutableListOf<SleepTimerState?>()

    /** The virtual test clock stands in for elapsed realtime. */
    private fun TestScope.timer() =
        SleepTimer(player, backgroundScope, now = { testScheduler.currentTime }, onStateChanged = { states += it })

    @Test
    fun fade_isFullUntilTheLastSeconds_thenLinearToZero() {
        assertEquals(1f, sleepFade(60_000), 0f)
        assertEquals(1f, sleepFade(SLEEP_FADE_MS), 0f)
        assertEquals(0.5f, sleepFade(SLEEP_FADE_MS / 2), 0.001f)
        assertEquals(0f, sleepFade(0), 0f)
        assertEquals(0f, sleepFade(-5), 0f)
    }

    @Test
    fun minutes_fadeOverTheLast20s_thenPauseAndRestoreTheVolume() = runTest {
        val timer = timer()
        timer.startMinutes(1)
        assertEquals(SleepTimerState.At(60_000), timer.state)

        advanceTimeBy(39_000); runCurrent()
        assertEquals(1f, player.lastFade, 0f)
        advanceTimeBy(11_000); runCurrent() // 10 s left
        assertEquals(0.5f, player.lastFade, 0.02f)
        assertEquals(0, player.paused)

        advanceTimeBy(10_100); runCurrent()
        assertEquals(1, player.paused)
        assertEquals(1f, player.lastFade, 0f)
        assertNull(timer.state)
        assertNull(states.last())
    }

    @Test
    fun cancel_stopsWithoutPausing_andRestoresTheVolume() = runTest {
        val timer = timer()
        timer.startMinutes(1)
        advanceTimeBy(50_000); runCurrent()
        assertTrue(player.lastFade < 1f)
        timer.cancel()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, player.paused)
        assertEquals(1f, player.lastFade, 0f)
        assertNull(timer.state)
    }

    @Test
    fun extend_addsFromTheCurrentEnd_andLiftsTheFade() = runTest {
        val timer = timer()
        timer.startMinutes(1)
        advanceTimeBy(50_000); runCurrent()
        timer.extend(15)
        assertEquals(SleepTimerState.At(60_000 + 15 * 60_000L), timer.state)
        assertEquals(1f, player.lastFade, 0f)
        // It now ends 15 minutes after the original end (60 s), not after the extension (50 s).
        advanceTimeBy(15 * 60_000L); runCurrent()
        assertEquals(0, player.paused)
        advanceTimeBy(10_100); runCurrent()
        assertEquals(1, player.paused)
    }

    @Test
    fun aNewTimerReplacesTheOld() = runTest {
        val timer = timer()
        timer.startMinutes(1)
        timer.startMinutes(30)
        advanceTimeBy(2 * 60_000L); runCurrent()
        assertEquals(0, player.paused)
        assertEquals(SleepTimerState.At(30 * 60_000L), timer.state)
    }

    @Test
    fun endOfTrack_pausesAtTheItemEnd_withAFadeBefore() = runTest {
        val timer = timer()
        player.remaining = 60_000
        timer.startEndOfTrack()
        assertTrue(player.pauseAtEnd)
        assertEquals(SleepTimerState.EndOfTrack, timer.state)

        player.remaining = 5_000
        advanceTimeBy(200); runCurrent()
        assertEquals(0.25f, player.lastFade, 0.01f)

        // The player paused itself at the end of the item.
        timer.onPausedAtEndOfItem()
        assertEquals(1, player.paused)
        assertFalse(player.pauseAtEnd)
        assertEquals(1f, player.lastFade, 0f)
        assertNull(timer.state)
    }

    @Test
    fun endOfTrack_unknownDuration_keepsFullVolume_andCancelClearsPauseAtEnd() = runTest {
        val timer = timer()
        timer.startEndOfTrack()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1f, player.lastFade, 0f)
        timer.cancel()
        assertFalse(player.pauseAtEnd)
        // An unrelated end-of-item pause after cancelling does nothing.
        timer.onPausedAtEndOfItem()
        assertEquals(0, player.paused)
    }

    @Test
    fun switchingFromEndOfTrackToMinutes_clearsPauseAtEnd() = runTest {
        val timer = timer()
        timer.startEndOfTrack()
        timer.startMinutes(10)
        assertFalse(player.pauseAtEnd)
    }
}
