package io.github.admilsonlandim.veyra.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerSettingsTest {

    private val caps = EqualizerCapabilities(
        bands = listOf(EqualizerBand(60_000), EqualizerBand(230_000), EqualizerBand(910_000), EqualizerBand(3_600_000), EqualizerBand(14_000_000)),
        minLevel = -1500,
        maxLevel = 1500,
        presets = listOf(
            EqualizerPreset("Normal", listOf(300, 0, 0, 0, 300)),
            EqualizerPreset("Rock", listOf(500, 300, -100, 300, 500)),
        ),
        bassBoostSupported = true,
    )

    @Test
    fun levels_roundTripThroughPreferences() {
        assertEquals(listOf(-300, 0, 150), decodeLevels(encodeLevels(listOf(-300, 0, 150))))
        assertEquals(emptyList<Int>(), decodeLevels(""))
        assertEquals(emptyList<Int>(), decodeLevels(null))
        assertEquals(emptyList<Int>(), decodeLevels("1,x,3"))
    }

    @Test
    fun movingASlider_switchesToCustom_fromThePresetLevels() {
        val rock = EqualizerSettings(enabled = true).withPreset(1, caps)
        val tweaked = rock.withBandLevel(band = 2, level = 600, capabilities = caps)
        assertEquals(CUSTOM_PRESET, tweaked.preset)
        assertEquals(listOf(500, 300, 600, 300, 500), tweaked.bandLevels)
    }

    @Test
    fun bandLevels_areClampedToTheDeviceRange() {
        val s = EqualizerSettings(bandLevels = List(5) { 0 }).withBandLevel(0, 9_000, caps)
        assertEquals(1500, s.bandLevels[0])
    }

    @Test
    fun storedLevelsForAnotherBandCount_fallBackToFlat() {
        val restored = EqualizerSettings(bandLevels = listOf(100, 200, 300))
        assertEquals(List(5) { 0 }, restored.levelsFor(caps))
    }

    @Test
    fun presetIndex_outOfRange_meansCustom() {
        val s = EqualizerSettings(bandLevels = List(5) { 50 }).withPreset(7, caps)
        assertEquals(CUSTOM_PRESET, s.preset)
        assertEquals(List(5) { 50 }, s.levelsFor(caps))
    }

    @Test
    fun selectedPreset_showsItsLevels() {
        assertEquals(listOf(300, 0, 0, 0, 300), EqualizerSettings(preset = 0).levelsFor(caps))
    }

    @Test
    fun bandFrequencyLabels() {
        assertEquals("60 Hz", formatBandFrequency(60_000))
        assertEquals("910 Hz", formatBandFrequency(910_000))
        assertEquals("3.6 kHz", formatBandFrequency(3_600_000))
        assertEquals("14 kHz", formatBandFrequency(14_000_000))
    }
}
