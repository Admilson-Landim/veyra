package com.example.veyra.data.clips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRulesTest {

    private val duration = 200_000L
    private val global = GlobalClipRule(skipStartMs = 10_000, skipEndMs = 15_000)

    private fun custom(clip: TrackClip, valid: Boolean = true) =
        ResolvedClip(rowIds = listOf(1), clip = clip, isValid = valid, mode = ClipMode.Custom)

    private val full = ResolvedClip(rowIds = listOf(2), clip = TrackClip(0, null), isValid = true, mode = ClipMode.Full)

    @Test
    fun noSettingAndGlobalOff_playsWholeFile() {
        assertEquals(EffectiveClip(null, AppliedRule.None), effectiveClip(null, GlobalClipRule(), duration))
    }

    @Test
    fun noSetting_usesGlobal() {
        assertEquals(
            EffectiveClip(TrackClip(10_000, 185_000), AppliedRule.Global),
            effectiveClip(null, global, duration),
        )
    }

    @Test
    fun globalStartOnly_keepsNaturalEnd() {
        assertEquals(TrackClip(10_000, null), globalClip(GlobalClipRule(skipStartMs = 10_000), duration))
    }

    @Test
    fun customBeatsGlobal() {
        val clip = TrackClip(30_000, 60_000)
        assertEquals(EffectiveClip(clip, AppliedRule.Custom), effectiveClip(custom(clip), global, duration))
    }

    @Test
    fun fullIgnoresGlobal() {
        assertEquals(EffectiveClip(null, AppliedRule.Full), effectiveClip(full, global, duration))
    }

    @Test
    fun invalidCustom_fallsBackToGlobal() {
        // The file got shorter than the stored end: the custom clip is ignored.
        val stale = custom(TrackClip(30_000, 400_000), valid = false)
        assertEquals(AppliedRule.Global, effectiveClip(stale, global, duration).rule)
    }

    @Test
    fun invalidCustomAndGlobalOff_playsWholeFile() {
        val stale = custom(TrackClip(30_000, 400_000), valid = false)
        assertEquals(EffectiveClip(null, AppliedRule.None), effectiveClip(stale, GlobalClipRule(), duration))
    }

    @Test
    fun shortTrack_isProtectedFromGlobal() {
        // 50 s track - 10 s - 15 s = 25 s left, below the 30 s minimum.
        assertNull(globalClip(global, 50_000))
        assertEquals(AppliedRule.None, effectiveClip(null, global, 50_000).rule)
        // Exactly the minimum remaining is allowed.
        assertEquals(TrackClip(10_000, 40_000), globalClip(global, 55_000))
    }

    @Test
    fun invalidGlobalValues_doNotApply() {
        assertNull(globalClip(GlobalClipRule(skipStartMs = 300_000), duration))
        assertNull(globalClip(global, 0))
        // Negative values are treated as zero.
        assertNull(globalClip(GlobalClipRule(skipStartMs = -5_000), duration))
    }
}
