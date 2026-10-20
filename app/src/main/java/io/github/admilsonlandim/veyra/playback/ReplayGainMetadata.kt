package io.github.admilsonlandim.veyra.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import io.github.admilsonlandim.veyra.data.audio.ReplayGain
import io.github.admilsonlandim.veyra.data.audio.replayGainFrom

/**
 * ReplayGain from the metadata ExoPlayer extracts while preparing an item: ID3 `TXXX` frames
 * (MP3) and Vorbis comments (FLAC, Ogg Vorbis). Not read: MP4/M4A freeform atoms and Opus's
 * R128 gains, so those files are levelled like untagged ones.
 */
@OptIn(UnstableApi::class)
fun replayGainOf(metadata: Metadata): ReplayGain? {
    val tags = (0 until metadata.length()).mapNotNull { index ->
        when (val entry = metadata.get(index)) {
            is TextInformationFrame -> entry.description?.let { it to entry.values.firstOrNull().orEmpty() }
            is VorbisComment -> entry.key to entry.value
            else -> null
        }
    }
    return replayGainFrom(tags)
}
