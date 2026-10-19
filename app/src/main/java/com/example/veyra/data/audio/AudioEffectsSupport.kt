package com.example.veyra.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Reads what an Equalizer instance offers: bands, level range and preset levels. */
fun Equalizer.readCapabilities(bassBoostSupported: Boolean): EqualizerCapabilities {
    val range = bandLevelRange
    val bandCount = numberOfBands.toInt()
    val bands = (0 until bandCount).map { EqualizerBand(getCenterFreq(it.toShort())) }
    val presets = (0 until numberOfPresets).map { index ->
        val preset = index.toShort()
        usePreset(preset)
        EqualizerPreset(
            name = getPresetName(preset),
            levels = (0 until bandCount).map { getBandLevel(it.toShort()).toInt() },
        )
    }
    return EqualizerCapabilities(bands, range[0].toInt(), range[1].toInt(), presets, bassBoostSupported)
}

/**
 * Device audio effect support, shared by the settings screen and the playback service.
 * Effects are per audio session, so the probe uses a throwaway session: the settings screen
 * can show the real bands without any music playing.
 */
object AudioEffectsSupport {

    /** Null until probed; [EqualizerProbe.Unsupported] when the device has no equalizer. */
    sealed interface EqualizerProbe {
        data class Supported(val capabilities: EqualizerCapabilities) : EqualizerProbe
        data object Unsupported : EqualizerProbe
    }

    @Volatile
    private var cached: EqualizerProbe? = null

    private val _sessionId = MutableStateFlow(AudioManager.AUDIO_SESSION_ID_GENERATE)

    /** The playback session the service applies effects to (for the system effects panel). */
    val playbackSessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun publishSessionId(sessionId: Int) {
        _sessionId.value = sessionId
    }

    fun isBassBoostSupported(): Boolean =
        runCatching { AudioEffect.queryEffects().any { it.type == AudioEffect.EFFECT_TYPE_BASS_BOOST } }.getOrDefault(false)

    suspend fun probe(context: Context): EqualizerProbe = cached ?: withContext(Dispatchers.IO) {
        val result = runCatching {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val session = audioManager.generateAudioSessionId()
            val equalizer = Equalizer(0, session)
            try {
                EqualizerProbe.Supported(equalizer.readCapabilities(isBassBoostSupported()))
            } finally {
                equalizer.release()
            }
        }.getOrElse { EqualizerProbe.Unsupported }
        result.also { cached = it }
    }

    /** Creates a BassBoost for [sessionId], or null when the device does not support it. */
    fun createBassBoost(sessionId: Int): BassBoost? =
        if (!isBassBoostSupported()) null else runCatching { BassBoost(0, sessionId) }.getOrNull()
}
