package com.example.veyra.data.media

import kotlinx.coroutines.CancellationException

/**
 * Largest power-of-two subsample factor that keeps both decoded dimensions at or above the
 * requested size. Decoding a 3000 px cover for a 192 px thumbnail with factor 8 allocates
 * 64x less memory than decoding it whole.
 */
fun calculateInSampleSize(width: Int, height: Int, requestedSize: Int): Int {
    if (width <= 0 || height <= 0 || requestedSize <= 0) return 1
    var sampleSize = 1
    while (width / (sampleSize * 2) >= requestedSize && height / (sampleSize * 2) >= requestedSize) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Tries each source in order and returns the first non-null result. A source that throws
 * (e.g. no thumbnail available, unreadable file) counts as "nothing here" and the next one is tried.
 */
fun <T : Any> firstAvailable(vararg sources: () -> T?): T? {
    for (source in sources) {
        val result = try {
            source()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (result != null) return result
    }
    return null
}

/** A crop rectangle; [right] and [bottom] are exclusive. */
data class CropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Bounds without the dark bars around a letterboxed image (e.g. a 16:9 video frame inside a 4:3
 * video thumbnail). Bars are whole rows/columns that are [isDarkRow]/[isDarkColumn]. Each side
 * loses at most [maxTrimFraction] of the size, and nothing is trimmed if less than half would
 * remain, so a genuinely dark picture is left alone.
 */
fun letterboxBounds(
    width: Int,
    height: Int,
    isDarkRow: (Int) -> Boolean,
    isDarkColumn: (Int) -> Boolean,
    maxTrimFraction: Float = 0.3f,
): CropBounds {
    val full = CropBounds(0, 0, width, height)
    if (width <= 0 || height <= 0) return full
    val maxRows = (height * maxTrimFraction).toInt()
    val maxCols = (width * maxTrimFraction).toInt()
    var top = 0
    while (top < maxRows && isDarkRow(top)) top++
    var bottom = height
    while (height - bottom < maxRows && isDarkRow(bottom - 1)) bottom--
    var left = 0
    while (left < maxCols && isDarkColumn(left)) left++
    var right = width
    while (width - right < maxCols && isDarkColumn(right - 1)) right--
    val bounds = CropBounds(left, top, right, bottom)
    return if (bounds.width * 2 < width || bounds.height * 2 < height) full else bounds
}
