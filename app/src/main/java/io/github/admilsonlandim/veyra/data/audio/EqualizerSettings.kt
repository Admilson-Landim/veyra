package io.github.admilsonlandim.veyra.data.audio

/** Preset index meaning "the user's own band levels". */
const val CUSTOM_PRESET = -1

/** Bass boost strength range defined by android.media.audiofx.BassBoost. */
const val BASS_STRENGTH_MAX = 1000

/** The user's sound settings, as stored. Band levels are in millibels, one per device band. */
data class EqualizerSettings(
    val enabled: Boolean = false,
    /** A device preset index, or [CUSTOM_PRESET]. */
    val preset: Int = CUSTOM_PRESET,
    val bandLevels: List<Int> = emptyList(),
    val bassBoostEnabled: Boolean = false,
    val bassStrength: Int = 500,
) {
    /** Moving a slider turns any preset into "custom", starting from the current levels. */
    fun withBandLevel(band: Int, level: Int, capabilities: EqualizerCapabilities): EqualizerSettings {
        val levels = levelsFor(capabilities).toMutableList()
        if (band !in levels.indices) return this
        levels[band] = level.coerceIn(capabilities.minLevel, capabilities.maxLevel)
        return copy(preset = CUSTOM_PRESET, bandLevels = levels)
    }

    /** Selecting a preset shows (and stores) its levels, so switching back to custom starts there. */
    fun withPreset(preset: Int, capabilities: EqualizerCapabilities): EqualizerSettings =
        if (preset !in capabilities.presets.indices) {
            copy(preset = CUSTOM_PRESET)
        } else {
            copy(preset = preset, bandLevels = capabilities.presets[preset].levels)
        }

    /**
     * Levels to show and apply on this device: the stored ones when they match the device's band
     * count (clamped to its range), otherwise flat (0 dB). Settings restored on another device, or
     * after a firmware update that changed the bands, never apply bogus values.
     */
    fun levelsFor(capabilities: EqualizerCapabilities): List<Int> {
        if (preset in capabilities.presets.indices) return capabilities.presets[preset].levels
        if (bandLevels.size != capabilities.bands.size) return List(capabilities.bands.size) { 0 }
        return bandLevels.map { it.coerceIn(capabilities.minLevel, capabilities.maxLevel) }
    }
}

/** One band of the device equalizer: its centre frequency in millihertz (Android's unit). */
data class EqualizerBand(val centerMilliHz: Int)

data class EqualizerPreset(val name: String, val levels: List<Int>)

/** What the device's equalizer offers. Read from the device, never invented. */
data class EqualizerCapabilities(
    val bands: List<EqualizerBand>,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<EqualizerPreset>,
    val bassBoostSupported: Boolean,
)

/** "62 Hz", "1 kHz", "14 kHz": band labels from the device's centre frequencies. */
fun formatBandFrequency(centerMilliHz: Int): String {
    val hz = centerMilliHz / 1000
    return if (hz >= 1000) {
        val khz = hz / 1000.0
        if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(java.util.Locale.ROOT, khz)
    } else {
        "$hz Hz"
    }
}

/** Band levels as stored in preferences: "-300,0,150". */
fun encodeLevels(levels: List<Int>): String = levels.joinToString(",")

/** Inverse of [encodeLevels]; anything malformed decodes to an empty list (= flat). */
fun decodeLevels(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    val parts = raw.split(',').map { it.trim().toIntOrNull() }
    return if (parts.any { it == null }) emptyList() else parts.filterNotNull()
}
