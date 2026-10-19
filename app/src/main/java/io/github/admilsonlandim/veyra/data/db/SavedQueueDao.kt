package io.github.admilsonlandim.veyra.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import io.github.admilsonlandim.veyra.data.media.TrackKey

/** One item of the last play queue, in order. Rewritten whole whenever the queue changes. */
@Entity(tableName = "saved_queue")
data class SavedQueueItemEntity(
    /** Position in the queue, 0-based. */
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
) {
    val key: TrackKey get() = TrackKey(mediaStoreId, relativePath, displayName, sizeBytes, durationMs)

    companion object {
        fun from(position: Int, key: TrackKey) = SavedQueueItemEntity(
            position = position,
            mediaStoreId = key.mediaStoreId,
            relativePath = key.relativePath,
            displayName = key.displayName,
            sizeBytes = key.sizeBytes,
            durationMs = key.durationMs,
        )
    }
}

@Dao
abstract class SavedQueueDao {

    @Query("SELECT * FROM saved_queue ORDER BY position")
    abstract suspend fun items(): List<SavedQueueItemEntity>

    @Query("DELETE FROM saved_queue")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<SavedQueueItemEntity>)

    /** Replaces the saved queue in one transaction, so a crash never leaves half a queue. */
    @Transaction
    open suspend fun replace(keys: List<TrackKey>) {
        clear()
        insertAll(keys.mapIndexed(SavedQueueItemEntity::from))
    }
}
