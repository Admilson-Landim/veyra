package io.github.admilsonlandim.veyra.data.media

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.db.TrackSettingsEntity
import io.github.admilsonlandim.veyra.data.db.VeyraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The clean-up against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class TrackReferenceCleanerTest {

    private lateinit var db: VeyraDatabase
    private var position = 0 to 0L
    private lateinit var cleaner: TrackReferenceCleaner

    private fun track(id: Long) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = "Song $id", artist = "Artist",
        album = null, albumId = 0, durationMs = 180_000, displayName = "song$id.mp3", relativePath = "Music/",
        sizeBytes = 3_000_000 + id, dateModifiedSeconds = 0,
    )

    private val removed = track(2)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VeyraDatabase::class.java).build()
        cleaner = TrackReferenceCleaner(
            playlists = db.playlistDao(),
            settings = db.trackSettingsDao(),
            queue = db.savedQueueDao(),
            readQueuePosition = { position },
            writeQueuePosition = { index, ms -> position = index to ms },
            now = { 100 },
        )
        val playlists = db.playlistDao()
        val a = playlists.createPlaylist("A", now = 1)
        listOf(1L, 2, 3, 2).forEach { playlists.addTrack(a, track(it), now = 1) }
        val b = playlists.createPlaylist("B", now = 1)
        playlists.addTrack(b, track(3), now = 1)
        db.trackSettingsDao().insert(TrackSettingsEntity.from(removed.toTrackKey(), TrackClip(10_000, 20_000), now = 1))
        db.trackSettingsDao().insert(TrackSettingsEntity.from(track(3).toTrackKey(), TrackClip(5_000, null), now = 1))
        db.savedQueueDao().replace(listOf(track(1), removed, track(3)).map { it.toTrackKey() })
        position = 2 to 30_000L
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries(name: String): List<Long> {
        val id = db.playlistDao().observePlaylists().first().first { it.name == name }.id
        return db.playlistDao().entries(id).sortedBy { it.position }.map { it.mediaStoreId }
    }

    @Test
    fun delete_removesPlaylistEntriesClipAndSavedQueueItem() = runTest {
        cleaner.clean(removed, RemovalMode.Delete)

        assertEquals(listOf(1L, 3L), entries("A"))
        assertEquals(listOf(0, 1), db.playlistDao().entries(db.playlistDao().observePlaylists().first().first { it.name == "A" }.id).map { it.position })
        assertEquals(listOf(3L), entries("B"))
        assertEquals(listOf(3L), db.trackSettingsDao().all().map { it.mediaStoreId })
        assertEquals(listOf(1L, 3L), db.savedQueueDao().items().map { it.mediaStoreId })
        assertEquals(1 to 30_000L, position)
    }

    @Test
    fun trash_keepsPlaylistsAndClip_butLeavesTheQueue() = runTest {
        cleaner.clean(removed, RemovalMode.Trash)

        assertEquals(listOf(1L, 2L, 3L, 2L), entries("A"))
        assertEquals(2, db.trackSettingsDao().all().size)
        assertEquals(listOf(1L, 3L), db.savedQueueDao().items().map { it.mediaStoreId })
    }
}
