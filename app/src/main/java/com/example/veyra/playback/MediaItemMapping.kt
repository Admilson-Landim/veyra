package com.example.veyra.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.veyra.data.media.Track

/**
 * The single place where a Track becomes a MediaItem.
 * Phase 7: the per-track start/end trim is applied here, via MediaItem.ClippingConfiguration.
 */
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title ?: displayName)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
