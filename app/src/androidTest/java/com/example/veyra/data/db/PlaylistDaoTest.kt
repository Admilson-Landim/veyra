package com.example.veyra.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.veyra.data.media.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: VeyraDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VeyraDatabase::class.java)
            .build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = "Song $id", artist = "Artist",
        album = null, albumId = 0, durationMs = 180_000, displayName = "song$id.mp3", relativePath = "Music/",
        sizeBytes = 3_000_000 + id, dateModifiedSeconds = 0,
    )

    private suspend fun titles(playlistId: Long) = dao.observeEntries(playlistId).first().map { it.title }

    @Test
    fun create_andListWithCount() = runTest {
        val id = dao.createPlaylist("Treino", now = 1)
        dao.addTrack(id, track(1), now = 2)
        dao.addTrack(id, track(2), now = 3)
        dao.createPlaylist("Vazia", now = 4)

        val summaries = dao.observePlaylists().first().associateBy { it.name }
        assertEquals(2, summaries.getValue("Treino").trackCount)
        assertEquals(0, summaries.getValue("Vazia").trackCount)
        assertEquals(3L, summaries.getValue("Treino").updatedAt)
    }

    @Test
    fun addTrack_appendsInOrder_andStoresTheTrackKey() = runTest {
        val id = dao.createPlaylist("P", now = 0)
        dao.addTrack(id, track(1), now = 1)
        dao.addTrack(id, track(2), now = 1)
        dao.addTrack(id, track(1), now = 1) // same song twice is allowed

        val entries = dao.entries(id)
        assertEquals(listOf(0, 1, 2), entries.map { it.position })
        assertEquals(listOf("Song 1", "Song 2", "Song 1"), entries.map { it.title })
        with(entries.first().key) {
            assertEquals(1L, mediaStoreId)
            assertEquals("Music/", relativePath)
            assertEquals("song1.mp3", displayName)
            assertEquals(3_000_001L, sizeBytes)
            assertEquals(180_000L, durationMs)
        }
    }

    @Test
    fun moveEntry_reordersAndKeepsPositionsContiguous() = runTest {
        val id = dao.createPlaylist("P", now = 0)
        (1L..4L).forEach { dao.addTrack(id, track(it), now = 0) }

        dao.moveEntry(id, from = 0, to = 2, now = 5)
        assertEquals(listOf("Song 2", "Song 3", "Song 1", "Song 4"), titles(id))
        dao.moveEntry(id, from = 3, to = 0, now = 6)
        assertEquals(listOf("Song 4", "Song 2", "Song 3", "Song 1"), titles(id))
        assertEquals(listOf(0, 1, 2, 3), dao.entries(id).map { it.position })

        dao.moveEntry(id, from = 0, to = 9, now = 7) // out of range: ignored
        assertEquals(listOf("Song 4", "Song 2", "Song 3", "Song 1"), titles(id))
    }

    @Test
    fun removeEntry_compactsPositions() = runTest {
        val id = dao.createPlaylist("P", now = 0)
        (1L..3L).forEach { dao.addTrack(id, track(it), now = 0) }
        val middle = dao.entries(id)[1]

        dao.removeEntry(id, middle.id, now = 1)

        assertEquals(listOf("Song 1", "Song 3"), titles(id))
        assertEquals(listOf(0, 1), dao.entries(id).map { it.position })
    }

    @Test
    fun rename_updatesNameAndTimestamp() = runTest {
        val id = dao.createPlaylist("Old", now = 0)
        dao.renamePlaylist(id, "New", now = 9)
        val playlist = dao.observePlaylist(id).first()
        assertEquals("New", playlist?.name)
        assertEquals(9L, playlist?.updatedAt)
    }

    @Test
    fun deletePlaylist_cascadesToItsEntries_only() = runTest {
        val doomed = dao.createPlaylist("Doomed", now = 0)
        val kept = dao.createPlaylist("Kept", now = 0)
        dao.addTrack(doomed, track(1), now = 0)
        dao.addTrack(doomed, track(2), now = 0)
        dao.addTrack(kept, track(3), now = 0)

        dao.deletePlaylist(doomed)

        assertNull(dao.observePlaylist(doomed).first())
        assertTrue(dao.entries(doomed).isEmpty())
        assertEquals(listOf("Song 3"), titles(kept))
    }
}
