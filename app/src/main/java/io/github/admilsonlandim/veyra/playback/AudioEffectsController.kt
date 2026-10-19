package io.github.admilsonlandim.veyra.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import io.github.admilsonlandim.veyra.data.audio.AudioEffectsSupport
import io.github.admilsonlandim.veyra.data.audio.EqualizerCapabilities
import io.github.admilsonlandim.veyra.data.audio.EqualizerSettings
import io.github.admilsonlandim.veyra.data.audio.readCapabilities

/**
 * Equalizer and bass boost attached to the player's audio session, inside the service.
 *
 * - [onSessionId]: (re)creates the effects for a new session and announces it with
 *   ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, so system or third-party equalizers can attach;
 *   the previous session is closed with ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.
 * - [update]: applies the user's settings (also called on every change).
 * - [release]: closes the session and frees the effects.
 *
 * Every call into android.media.audiofx is guarded: some devices have no equalizer, and some
 * vendors' sound stacks reject or replace app effects. Failure just means "no effect".
 */
class AudioEffectsController(private val context: Context) {

    private var sessionId = NO_SESSION
    private var equalizer: Equalizer? = null
    private var capabilities: EqualizerCapabilities? = null
    private var bassBoost: BassBoost? = null
    private var settings = EqualizerSettings()

    fun onSessionId(newSessionId: Int) {
        if (newSessionId == sessionId) return
        release()
        if (newSessionId == NO_SESSION) return
        sessionId = newSessionId
        AudioEffectsSupport.publishSessionId(newSessionId)
        context.sendBroadcast(controlSessionIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION))
        equalizer = runCatching { Equalizer(0, newSessionId) }.getOrNull()
        capabilities = equalizer?.let { eq ->
            runCatching { eq.readCapabilities(AudioEffectsSupport.isBassBoostSupported()) }.getOrNull()
        }
        bassBoost = AudioEffectsSupport.createBassBoost(newSessionId)
        apply()
    }

    fun update(newSettings: EqualizerSettings) {
        settings = newSettings
        apply()
    }

    private fun apply() {
        val eq = equalizer
        val caps = capabilities
        if (eq != null && caps != null) {
            runCatching {
                if (settings.enabled) {
                    if (settings.preset in caps.presets.indices) {
                        eq.usePreset(settings.preset.toShort())
                    } else {
                        settings.levelsFor(caps).forEachIndexed { band, level ->
                            eq.setBandLevel(band.toShort(), level.toShort())
                        }
                    }
                }
                eq.enabled = settings.enabled
            }
        }
        bassBoost?.let { bass ->
            runCatching {
                val on = settings.enabled && settings.bassBoostEnabled
                if (on && bass.strengthSupported) bass.setStrength(settings.bassStrength.toShort())
                bass.enabled = on
            }
        }
    }

    fun release() {
        if (sessionId != NO_SESSION) {
            context.sendBroadcast(controlSessionIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION))
        }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        capabilities = null
        bassBoost = null
        sessionId = NO_SESSION
    }

    private fun controlSessionIntent(action: String) = Intent(action)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
}

/** "No session yet": same value as Media3's C.AUDIO_SESSION_ID_UNSET, without its unstable API. */
private const val NO_SESSION = AudioManager.AUDIO_SESSION_ID_GENERATE
