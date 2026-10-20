package io.github.admilsonlandim.veyra.data.audio

import kotlin.math.pow

/** ReplayGain values read from a file's tags, in dB. Null when the tag is missing. */
data class ReplayGain(val trackGainDb: Float?, val albumGainDb: Float?)

/** Which gain "Balanced volume" uses: per track (each song evened out) or per album (keeps an album's dynamics). */
enum class ReplayGainMode { Track, Album }

/** "-7.43 dB", "+1.2 dB", "-7,43" -> the number in dB; null if it is not one. */
fun parseGainDb(value: String): Float? =
    value.trim().removeSuffix("dB").removeSuffix("DB").removeSuffix("db").trim()
        .replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

/**
 * ReplayGain from tag key/value pairs, whatever the format: ID3 TXXX descriptions and Vorbis
 * comment keys use the same names, in any case. Null when the file has neither gain.
 */
fun replayGainFrom(tags: List<Pair<String, String>>): ReplayGain? {
    fun gain(name: String) = tags.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second?.let(::parseGainDb)
    val track = gain("REPLAYGAIN_TRACK_GAIN")
    val album = gain("REPLAYGAIN_ALBUM_GAIN")
    return if (track == null && album == null) null else ReplayGain(track, album)
}

fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

/**
 * The player volume factor for [gain]: the chosen gain (the other one if it is missing), applied
 * only as attenuation. Positive gains (quiet tracks) are not applied: raising the level could
 * clip, and the files' peaks are often not tagged. Clamped to [MIN_REPLAY_GAIN_DB] so a wrong tag
 * cannot make a track inaudible. Null when there is no gain: the caller levels it another way.
 */
fun replayGainVolume(gain: ReplayGain?, mode: ReplayGainMode): Float? {
    gain ?: return null
    val db = when (mode) {
        ReplayGainMode.Track -> gain.trackGainDb ?: gain.albumGainDb
        ReplayGainMode.Album -> gain.albumGainDb ?: gain.trackGainDb
    } ?: return null
    return dbToLinear(db.coerceIn(MIN_REPLAY_GAIN_DB, 0f))
}

const val MIN_REPLAY_GAIN_DB = -20f

/** The single place that decides the player volume: ReplayGain factor times the sleep fade. */
fun finalVolume(replayGain: Float, fade: Float): Float = (replayGain * fade).coerceIn(0f, 1f)
