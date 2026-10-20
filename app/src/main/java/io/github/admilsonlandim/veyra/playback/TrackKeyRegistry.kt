package io.github.admilsonlandim.veyra.playback

import androidx.media3.common.MediaItem
import io.github.admilsonlandim.veyra.data.media.TrackKey

/**
 * Keeps the [TrackKey] of every item queued in the playback service, inside the service only.
 *
 * The UI builds items with their key in the metadata extras ([toMediaItem]); the session passes
 * them through [admit] before they reach the player, which keeps the key here and removes it from
 * the item. The key holds the file's folder and name: in the player's items it would be sent to
 * every connected controller (system UI, Bluetooth, other apps). The queue saver reads it back
 * with [keyOf]. Main thread only, like the session callbacks and the player.
 */
class TrackKeyRegistry {

    private val keys = HashMap<String, TrackKey>()

    /** [items] as the player should hold them: without their key, which is remembered here. */
    fun admit(items: List<MediaItem>): List<MediaItem> = items.map { item ->
        val key = item.trackKey() ?: return@map item
        keys[item.mediaId] = key
        item.withoutTrackKey()
    }

    /** The key of an item admitted earlier, or null. */
    fun keyOf(item: MediaItem): TrackKey? = keys[item.mediaId]
}
