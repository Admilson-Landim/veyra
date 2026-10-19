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
