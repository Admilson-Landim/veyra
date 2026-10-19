package com.example.veyra.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.veyra.data.media.Track
import com.example.veyra.data.playlists.moved
import kotlinx.coroutines.flow.Flow

/**
 * Reads are Flows that re-emit on every change; writes are suspend functions. Operations that
 * touch several rows (add, remove, move) run in one transaction so positions stay contiguous.
 *
 * `now` is passed in (epoch millis) rather than read here, to keep the DAO deterministic in tests.
 */
@Dao
abstract class PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.updated_at AS updatedAt, COUNT(t.id) AS trackCount
        FROM playlists p LEFT JOIN playlist_tracks t ON t.playlist_id = p.id
        GROUP BY p.id
        """,
    )
    abstract fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    abstract fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    abstract fun observeEntries(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    abstract suspend fun entries(playlistId: Long): List<PlaylistTrackEntity>

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    suspend fun createPlaylist(name: String, now: Long): Long =
        insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, name: String, now: Long)

    /** Entries are removed by the foreign key's ON DELETE CASCADE. */
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylist(playlistId: Long)

    @Transaction
    open suspend fun addTrack(playlistId: Long, track: Track, now: Long): Long {
        val entryId = insertEntry(
            PlaylistTrackEntity(
                playlistId = playlistId,
                position = maxPosition(playlistId) + 1,
                mediaStoreId = track.id,
                relativePath = track.relativePath,
                displayName = track.displayName,
                sizeBytes = track.sizeBytes,
                durationMs = track.durationMs,
                title = track.title,
                artist = track.artist,
                addedAt = now,
            ),
        )
        touch(playlistId, now)
        return entryId
    }

    @Transaction
    open suspend fun removeEntry(playlistId: Long, entryId: Long, now: Long) {
        deleteEntry(entryId)
        writePositions(entries(playlistId))
        touch(playlistId, now)
    }

    /** Moves the entry at index [from] to index [to]; out-of-range indexes do nothing. */
    @Transaction
    open suspend fun moveEntry(playlistId: Long, from: Int, to: Int, now: Long) {
        val current = entries(playlistId)
        val reordered = current.moved(from, to)
        if (reordered === current) return
        writePositions(reordered)
        touch(playlistId, now)
    }

    private suspend fun writePositions(ordered: List<PlaylistTrackEntity>) {
        ordered.forEachIndexed { index, entry ->
            if (entry.position != index) setPosition(entry.id, index)
        }
    }

    @Insert
    abstract suspend fun insertEntry(entry: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE id = :entryId")
    abstract suspend fun deleteEntry(entryId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE id = :entryId")
    abstract suspend fun setPosition(entryId: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :playlistId")
    abstract suspend fun touch(playlistId: Long, now: Long)
}
