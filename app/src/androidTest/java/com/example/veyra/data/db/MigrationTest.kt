package com.example.veyra.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.veyra.data.clips.ClipMode
import com.example.veyra.data.clips.TrackClip
import com.example.veyra.data.media.TrackKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VeyraDatabase::class.java)

    @Test
    fun migrate1To2_keepsPlaylistsAndAddsTrackSettings() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Treino', 10, 20)")
            execSQL(
                "INSERT INTO playlist_tracks (id, playlist_id, position, media_store_id, relative_path, display_name, " +
                    "size_bytes, duration_ms, title, artist, added_at) " +
                    "VALUES (1, 1, 0, 42, 'Music/', 'song.mp3', 3000, 180000, 'Song', 'Artist', 30)",
            )
            close()
        }

        // Validates the migrated schema against app/schemas/.../2.json.
        helper.runMigrationsAndValidate(dbName, 2, true, VeyraDatabase.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VeyraDatabase::class.java,
            dbName,
        ).addMigrations(VeyraDatabase.MIGRATION_1_2, VeyraDatabase.MIGRATION_2_3).build()
        try {
            val playlists = db.playlistDao().observePlaylists().first()
            assertEquals(listOf("Treino"), playlists.map { it.name })
            assertEquals(1, playlists.single().trackCount)

            val settings = db.trackSettingsDao()
            val key = TrackKey(42, "Music/", "song.mp3", 3000, 180_000)
            settings.replace(emptyList(), TrackSettingsEntity.from(key, TrackClip(5_000, 170_000), now = 1))
            val stored = settings.observeAll().first().single().toStoredClip()
            assertEquals(key, stored.key)
            assertEquals(TrackClip(5_000, 170_000), stored.clip)

            settings.replace(listOf(stored.rowId), null) // reset
            assertEquals(0, settings.observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate2To3_keepsClipsAsCustom_andStoresFullMode() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO track_settings (id, media_store_id, relative_path, display_name, size_bytes, " +
                    "duration_ms, start_ms, end_ms, updated_at) VALUES (1, 42, 'Music/', 'song.mp3', 3000, 180000, 5000, 170000, 7)",
            )
            close()
        }

        // Validates the migrated schema against app/schemas/.../3.json.
        helper.runMigrationsAndValidate(dbName, 3, true, VeyraDatabase.MIGRATION_2_3).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), VeyraDatabase::class.java, dbName)
            .addMigrations(VeyraDatabase.MIGRATION_1_2, VeyraDatabase.MIGRATION_2_3)
            .build()
        try {
            val settings = db.trackSettingsDao()
            val existing = settings.observeAll().first().single().toStoredClip()
            assertEquals(ClipMode.Custom, existing.mode)
            assertEquals(TrackClip(5_000, 170_000), existing.clip)

            val key = TrackKey(43, "Music/", "other.mp3", 1000, 90_000)
            settings.replace(emptyList(), TrackSettingsEntity.from(key, TrackClip(0, null), now = 8, mode = ClipMode.Full))
            val full = settings.observeAll().first().map { it.toStoredClip() }.single { it.key == key }
            assertEquals(ClipMode.Full, full.mode)
        } finally {
            db.close()
        }
    }
}
