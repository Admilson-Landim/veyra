package io.github.admilsonlandim.veyra.playback

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * "Balanced volume" for tracks without ReplayGain: a gentle leveller on the player's audio
 * session (Android 9+). One compressor band over the whole spectrum lowers loud passages
 * (threshold -24 dBFS, ratio 3:1, slow enough not to pump), some make-up gain lifts the quiet
 * ones, and a limiter at -1 dBFS keeps peaks from clipping.
 *
 * It evens out loudness, not perfectly: it reacts to the music as it plays, unlike ReplayGain's
 * measured value. Some vendors' audio stacks reject or ignore app effects; then nothing happens.
 */
class VolumeLeveler {

    private var effect: Any? = null
    private var sessionId = 0
    private var wanted = false

    fun onSessionId(newSessionId: Int) {
        if (newSessionId == sessionId) return
        release()
        sessionId = newSessionId
        apply()
    }

    fun setEnabled(enabled: Boolean) {
        wanted = enabled
        apply()
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || sessionId <= 0) return
        if (wanted && effect == null) effect = runCatching { create(sessionId) }.getOrNull()
        (effect as? DynamicsProcessing)?.let { dp -> runCatching { dp.enabled = wanted } }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun create(session: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ false, 0,
            /* mbcInUse = */ true, 1,
            /* postEqInUse = */ false, 0,
            /* limiterInUse = */ true,
        ).build()
        config.setMbcBandAllChannelsTo(
            0,
            DynamicsProcessing.MbcBand(
                true, 20_000f,
                /* attackTime ms */ 30f, /* releaseTime ms */ 400f,
                /* ratio */ 3f, /* threshold dB */ -24f, /* kneeWidth dB */ 6f,
                /* noiseGateThreshold dB */ -90f, /* expanderRatio */ 1f,
                /* preGain dB */ 0f, /* postGain dB */ 6f,
            ),
        )
        config.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true, true, 0,
                /* attackTime ms */ 1f, /* releaseTime ms */ 60f,
                /* ratio */ 10f, /* threshold dB */ -1f, /* postGain dB */ 0f,
            ),
        )
        return DynamicsProcessing(0, session, config)
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { (effect as? DynamicsProcessing)?.release() }
        effect = null
        sessionId = 0
    }
}
