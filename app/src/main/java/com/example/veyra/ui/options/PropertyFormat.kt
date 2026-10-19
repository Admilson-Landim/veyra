package com.example.veyra.ui.options

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Pure formatters for the "Properties" sheet. They return null for unknown values; the UI then
// shows "Desconhecido".

/** 320000 -> "320 kbps". */
fun formatBitrate(bitsPerSecond: Int?): String? =
    bitsPerSecond?.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kbps" }

/** 44100 -> "44,1 kHz" (decimal separator from [locale]). */
fun formatSampleRate(hertz: Int?, locale: Locale = Locale.getDefault()): String? =
    hertz?.takeIf { it > 0 }?.let {
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        "${format.format(it / 1000.0)} kHz"
    }

/** Decimal units, like Android's own storage screens: 4256684 -> "4,3 MB". */
fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String? {
    if (bytes <= 0) return null
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (unit == 0) 0 else 1 }
    return "${format.format(value)} ${units[unit]}"
}

/** "03/12" -> "3/12", "7" -> "7"; anything not numeric is shown as is. */
fun formatTrackNumber(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.split('/')?.joinToString("/") { part ->
        part.trim().toIntOrNull()?.toString() ?: part.trim()
    }

/** Keeps the year of a date tag: "2019-05-02T00:00:00" -> "2019". */
fun formatYear(raw: String?): String? =
    raw?.trim()?.let { Regex("""\b(1[89]\d\d|20\d\d)\b""").find(it)?.value ?: it.takeIf { v -> v.isNotEmpty() } }

/** "audio/mpeg" -> "MP3 (audio/mpeg)"; unknown types are shown as the raw MIME type. */
fun formatMimeType(mime: String?): String? {
    val value = mime?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val name = when (value) {
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac", "audio/mp4a-latm" -> "AAC"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/ogg", "application/ogg", "audio/vorbis" -> "Ogg Vorbis"
        "audio/opus" -> "Opus"
        "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
        "audio/amr", "audio/3gpp" -> "AMR"
        else -> return value
    }
    return "$name ($value)"
}

/** Date and time in the user's locale and time zone. */
fun formatDateAdded(
    epochSeconds: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String? {
    if (epochSeconds <= 0) return null
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).apply {
        this.timeZone = timeZone
    }
    return format.format(Date(epochSeconds * 1000))
}
