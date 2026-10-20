package io.github.admilsonlandim.veyra.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTest {

    @Test
    fun parsesGainValues() {
        assertEquals(-7.43f, parseGainDb("-7.43 dB")!!, 0.001f)
        assertEquals(1.2f, parseGainDb("+1.20 dB")!!, 0.001f)
        assertEquals(-3.5f, parseGainDb(" -3,5 dB ")!!, 0.001f)
        assertEquals(-6f, parseGainDb("-6")!!, 0f)
        assertNull(parseGainDb("loud"))
        assertNull(parseGainDb(""))
        assertNull(parseGainDb("NaN dB"))
    }

    @Test
    fun readsTagsInAnyCase() {
        val gain = replayGainFrom(listOf("replaygain_track_gain" to "-8.1 dB", "REPLAYGAIN_ALBUM_GAIN" to "-6.0 dB", "TITLE" to "x"))
        assertEquals(ReplayGain(-8.1f, -6f), gain)
        assertNull(replayGainFrom(listOf("TITLE" to "x")))
        assertEquals(ReplayGain(null, -6f), replayGainFrom(listOf("REPLAYGAIN_ALBUM_GAIN" to "-6 dB")))
    }

    @Test
    fun dbToLinear_matchesTheDefinition() {
        assertEquals(1f, dbToLinear(0f), 0.0001f)
        assertEquals(0.5012f, dbToLinear(-6f), 0.0005f)
        assertEquals(0.1f, dbToLinear(-20f), 0.0001f)
        assertEquals(1.9953f, dbToLinear(6f), 0.0005f)
    }

    @Test
    fun volume_usesTheChosenGain_fallingBackToTheOther() {
        val both = ReplayGain(trackGainDb = -6f, albumGainDb = -3f)
        assertEquals(dbToLinear(-6f), replayGainVolume(both, ReplayGainMode.Track)!!, 0.0001f)
        assertEquals(dbToLinear(-3f), replayGainVolume(both, ReplayGainMode.Album)!!, 0.0001f)
        assertEquals(dbToLinear(-6f), replayGainVolume(ReplayGain(-6f, null), ReplayGainMode.Album)!!, 0.0001f)
        assertNull(replayGainVolume(null, ReplayGainMode.Track))
        assertNull(replayGainVolume(ReplayGain(null, null), ReplayGainMode.Track))
    }

    @Test
    fun volume_onlyAttenuates_andIsLimited() {
        // A quiet track's positive gain is not applied: no boost, no clipping.
        assertEquals(1f, replayGainVolume(ReplayGain(4.5f, null), ReplayGainMode.Track)!!, 0f)
        // A broken tag cannot make a track inaudible.
        assertEquals(dbToLinear(MIN_REPLAY_GAIN_DB), replayGainVolume(ReplayGain(-60f, null), ReplayGainMode.Track)!!, 0.0001f)
    }

    @Test
    fun finalVolume_combinesReplayGainAndFade_withinRange() {
        assertEquals(0.25f, finalVolume(0.5f, 0.5f), 0.0001f)
        assertEquals(1f, finalVolume(1f, 1f), 0f)
        assertEquals(0f, finalVolume(0.5f, 0f), 0f)
        assertEquals(1f, finalVolume(2f, 1f), 0f)
    }
}
