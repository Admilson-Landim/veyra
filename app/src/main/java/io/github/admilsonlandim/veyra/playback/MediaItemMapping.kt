package io.github.admilsonlandim.veyra.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.clips.clipToApply
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackKey

/** MediaMetadata extra holding the track's content:// URI. */
const val EXTRA_CONTENT_URI = "io.github.admilsonlandim.veyra.CONTENT_URI"

// MediaMetadata extras carrying the track's TrackKey, so the playback service can save the queue
// in a form that survives a MediaStore re-index (see SavedQueue). The service takes them out as
// the items enter the session (see TrackKeyRegistry).
private const val EXTRA_KEY_PATH = "io.github.admilsonlandim.veyra.KEY_PATH"
private const val EXTRA_KEY_NAME = "io.github.admilsonlandim.veyra.KEY_NAME"
private const val EXTRA_KEY_SIZE = "io.github.admilsonlandim.veyra.KEY_SIZE"
private const val EXTRA_KEY_DURATION = "io.github.admilsonlandim.veyra.KEY_DURATION"

/** The TrackKey stored by [toMediaItem], or null for items not created by it. */
fun MediaItem.trackKey(): TrackKey? {
    val extras = mediaMetadata.extras ?: return null
    val id = mediaId.toLongOrNull() ?: return null
    if (!extras.containsKey(EXTRA_KEY_SIZE)) return null
    return TrackKey(
        mediaStoreId = id,
        relativePath = extras.getString(EXTRA_KEY_PATH),
        displayName = extras.getString(EXTRA_KEY_NAME),
        sizeBytes = extras.getLong(EXTRA_KEY_SIZE),
        durationMs = extras.getLong(EXTRA_KEY_DURATION),
    )
}

/**
 * This item without its TrackKey extras (folder, file name, size, duration): what the player
 * holds, so they never reach other controllers (see TrackKeyRegistry). The content URI stays: the
 * notification's artwork loader needs it, and it reveals nothing without storage permission.
 */
fun MediaItem.withoutTrackKey(): MediaItem {
    val extras = mediaMetadata.extras ?: return this
    if (!extras.containsKey(EXTRA_KEY_SIZE)) return this
    val kept = Bundle().apply { extras.getString(EXTRA_CONTENT_URI)?.let { putString(EXTRA_CONTENT_URI, it) } }
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(kept).build()).build()
}

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
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_CONTENT_URI, contentUri)
                        putString(EXTRA_KEY_PATH, relativePath)
                        putString(EXTRA_KEY_NAME, displayName)
                        putLong(EXTRA_KEY_SIZE, sizeBytes)
                        putLong(EXTRA_KEY_DURATION, durationMs)
                    },
                )
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
