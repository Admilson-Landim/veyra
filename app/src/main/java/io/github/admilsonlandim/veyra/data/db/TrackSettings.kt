package io.github.admilsonlandim.veyra.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import io.github.admilsonlandim.veyra.data.clips.ClipMode
import io.github.admilsonlandim.veyra.data.clips.StoredClip
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.TrackKey
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
    /** [ClipMode] name: "Custom" (play the clip) or "Full" (always the whole file). Since v3. */
    @ColumnInfo(name = "mode", defaultValue = MODE_CUSTOM) val mode: String = MODE_CUSTOM,
) {
    fun toStoredClip() = StoredClip(
        rowId = id,
        key = TrackKey(mediaStoreId, relativePath, displayName, sizeBytes, durationMs),
        clip = TrackClip(startMs, endMs),
        updatedAt = updatedAt,
        // Unknown values (e.g. written by a newer version) fall back to the clip.
        mode = ClipMode.entries.firstOrNull { it.name == mode } ?: ClipMode.Custom,
    )

    companion object {
        const val MODE_CUSTOM = "Custom"

        fun from(key: TrackKey, clip: TrackClip, now: Long, mode: ClipMode = ClipMode.Custom) = TrackSettingsEntity(
            mediaStoreId = key.mediaStoreId,
            relativePath = key.relativePath,
            displayName = key.displayName,
            sizeBytes = key.sizeBytes,
            durationMs = key.durationMs,
            startMs = clip.startMs,
            endMs = clip.endMs,
            updatedAt = now,
            mode = mode.name,
        )
    }
}

@Dao
abstract class TrackSettingsDao {

    /** The whole table: it holds one row per customised track, so it stays small. */
    @Query("SELECT * FROM track_settings")
    abstract fun observeAll(): Flow<List<TrackSettingsEntity>>

    @Query("SELECT * FROM track_settings")
    abstract suspend fun all(): List<TrackSettingsEntity>

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
