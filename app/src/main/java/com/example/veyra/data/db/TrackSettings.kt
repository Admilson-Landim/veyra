package com.example.veyra.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.veyra.data.clips.StoredClip
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.media.TrackKey
import kotlinx.coroutines.flow.Flow

/**
 * Per-track settings, keyed by [TrackKey] (see there) so they follow the file across
 * MediaStore re-indexes. For now it holds the start/end clip; [endMs] null = natural end.
 */
@Entity(
    tableName = "track_settings",
    indices = [Index(value = ["media_store_id"])],
)
data class TrackSettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toStoredClip() = StoredClip(
        rowId = id,
        key = TrackKey(mediaStoreId, relativePath, displayName, sizeBytes, durationMs),
        clip = TrackClip(startMs, endMs),
        updatedAt = updatedAt,
    )

    companion object {
        fun from(key: TrackKey, clip: TrackClip, now: Long) = TrackSettingsEntity(
            mediaStoreId = key.mediaStoreId,
            relativePath = key.relativePath,
            displayName = key.displayName,
            sizeBytes = key.sizeBytes,
            durationMs = key.durationMs,
            startMs = clip.startMs,
            endMs = clip.endMs,
            updatedAt = now,
        )
    }
}

@Dao
abstract class TrackSettingsDao {

    /** The whole table: it holds one row per customised track, so it stays small. */
    @Query("SELECT * FROM track_settings")
    abstract fun observeAll(): Flow<List<TrackSettingsEntity>>

    @Insert
    abstract suspend fun insert(entity: TrackSettingsEntity): Long

    @Query("DELETE FROM track_settings WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    /** Removes the track's previous rows and, unless [replacement] is null (reset), stores the new one. */
    @Transaction
    open suspend fun replace(previousRowIds: List<Long>, replacement: TrackSettingsEntity?) {
        if (previousRowIds.isNotEmpty()) deleteByIds(previousRowIds)
        if (replacement != null) insert(replacement)
    }
}
