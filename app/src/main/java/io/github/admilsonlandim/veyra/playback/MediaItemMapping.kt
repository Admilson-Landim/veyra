package io.github.admilsonlandim.veyra.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.clips.clipToApply
import io.github.admilsonlandim.veyra.data.media.Track

/** MediaMetadata extra holding the track's content:// URI. */
const val EXTRA_CONTENT_URI = "io.github.admilsonlandim.veyra.CONTENT_URI"

/**
 * The single place where a Track becomes a MediaItem.
 *
 * [clip] is the track's stored start/end, if any. It is applied with ClippingConfiguration only
 * when it still fits the file (see [clipToApply]); otherwise the whole file plays. With a clip,
 * the player's position and duration are relative to the clipped range, and reaching its end
 * moves on to the next item exactly like the natural end of the file.
 */
fun Track.toMediaItem(clip: TrackClip? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        // The playback URI (localConfiguration) is not guaranteed to reach controllers;
        // RequestMetadata is, and the UI uses it to load the cover.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(contentUri.toUri()).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title ?: displayName)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                // Lets the notification's bitmap loader find a cover stored as a separate image.
                .setExtras(Bundle().apply { putString(EXTRA_CONTENT_URI, contentUri) })
                .build(),
        )
        .apply {
            clipToApply(clip, durationMs)?.let { applied ->
                setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(applied.startMs)
                        .setEndPositionMs(applied.endMs ?: C.TIME_END_OF_SOURCE)
                        .build(),
                )
            }
        }
        .build()
