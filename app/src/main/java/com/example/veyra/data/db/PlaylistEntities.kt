package com.example.veyra.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.veyra.data.media.TrackKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One entry of a playlist. Entries have their own id, so the same song can appear twice.
 * The track is stored as a [TrackKey] (see there), plus a title/artist snapshot used to show
 * the entry when the file is no longer on the device.
 */
@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlist_id", "position"]),
        Index(value = ["media_store_id"]),
    ],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    /** 0-based, contiguous within a playlist. */
    val position: Int,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val title: String?,
    val artist: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
) {
    val key: TrackKey get() = TrackKey(mediaStoreId, relativePath, displayName, sizeBytes, durationMs)
}

/** Row of the playlist list: the playlist plus how many entries it has. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val trackCount: Int,
)
