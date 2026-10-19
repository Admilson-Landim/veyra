package io.github.admilsonlandim.veyra.playback

/**
 * Decides whether to skip to the next item after a playback error.
 *
 * Each item in the queue gets at most one attempt per run of consecutive failures, so a
 * queue where every file is broken (e.g. with repeat-all on) stops instead of looping forever.
 */
class ErrorSkipPolicy {
    private var consecutiveErrors = 0

    /** Call when audio actually starts, or when a new queue is set. */
    fun reset() {
        consecutiveErrors = 0
    }

    fun shouldSkip(queueSize: Int, hasNext: Boolean): Boolean {
        consecutiveErrors++
        return hasNext && consecutiveErrors < queueSize
    }
}
