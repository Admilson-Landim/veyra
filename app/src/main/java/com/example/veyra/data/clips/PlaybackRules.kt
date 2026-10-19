package com.example.veyra.data.clips

/**
 * Global start/end rule for every track without its own setting (Settings > Playback).
 * 0 means "off" for that end.
 */
data class GlobalClipRule(val skipStartMs: Long = 0, val skipEndMs: Long = 0) {
    val isActive: Boolean get() = skipStartMs > 0 || skipEndMs > 0
}

/**
 * The global rule is only applied when at least this much of the track remains. Short files
 * (jingles, interludes, voice clips) would otherwise be cut to almost nothing: with "skip 30 s"
 * and "end 30 s early", a 1-minute track would vanish. 30 s also matches the default "short
 * track" threshold of the library settings.
 */
const val MIN_GLOBAL_REMAINING_MS = 30_000L

/** Which rule decided how a track plays, for the UI indicator. */
enum class AppliedRule { None, Global, Custom, Full }

/** The clip to play and where it came from. [clip] null = the whole file. */
data class EffectiveClip(val clip: TrackClip?, val rule: AppliedRule)

/**
 * Priority: the track's own setting > the global rule > the whole file.
 *
 * - [ClipMode.Full]: whole file, global rule ignored.
 * - [ClipMode.Custom]: its clip when it still fits the file; a clip that no longer fits is
 *   ignored as if not set, so the global rule (if any) applies instead.
 * - No setting ([stored] null): the global rule when active and when at least
 *   [MIN_GLOBAL_REMAINING_MS] would remain; otherwise the whole file.
 */
fun effectiveClip(stored: ResolvedClip?, global: GlobalClipRule, durationMs: Long): EffectiveClip {
    when (stored?.mode) {
        ClipMode.Full -> return EffectiveClip(null, AppliedRule.Full)
        ClipMode.Custom -> clipToApply(stored.clip, durationMs)?.let { return EffectiveClip(it, AppliedRule.Custom) }
        null -> Unit
    }
    return globalClip(global, durationMs)?.let { EffectiveClip(it, AppliedRule.Global) }
        ?: EffectiveClip(null, AppliedRule.None)
}

/** The global rule as a clip for a track of [durationMs], or null when it does not apply. */
fun globalClip(global: GlobalClipRule, durationMs: Long): TrackClip? {
    if (!global.isActive || durationMs <= 0) return null
    val start = global.skipStartMs.coerceAtLeast(0)
    val end = durationMs - global.skipEndMs.coerceAtLeast(0)
    if (end - start < MIN_GLOBAL_REMAINING_MS) return null
    return TrackClip(start, if (global.skipEndMs > 0) end else null)
}
