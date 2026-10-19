package io.github.admilsonlandim.veyra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App database. Every schema change from version 1 on needs a Migration added to the builder
 * below; destructive fallback is deliberately not used, so user data is never wiped.
 * Exported schemas live in app/schemas (one JSON per version).
 *
 * History:
 * 1. Playlists and their entries (Phase 6).
 * 2. track_settings: per-track start/end clip (Phase 7).
 * 3. track_settings.mode: "Custom" clip or "Full" (always the whole file) (Phase 9).
 * 4. saved_queue: the last play queue, to resume it after the app is closed (Phase 12).
 */
@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        TrackSettingsEntity::class,
        SavedQueueItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class VeyraDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    abstract fun trackSettingsDao(): TrackSettingsDao

    abstract fun savedQueueDao(): SavedQueueDao

    companion object {
        private const val NAME = "veyra.db"

        /** Adds track_settings. The SQL matches app/schemas/.../2.json exactly. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_settings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`media_store_id` INTEGER NOT NULL, " +
                        "`relative_path` TEXT, " +
                        "`display_name` TEXT, " +
                        "`size_bytes` INTEGER NOT NULL, " +
                        "`duration_ms` INTEGER NOT NULL, " +
                        "`start_ms` INTEGER NOT NULL, " +
                        "`end_ms` INTEGER, " +
                        "`updated_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_track_settings_media_store_id` " +
                        "ON `track_settings` (`media_store_id`)",
                )
            }
        }

        /** Adds track_settings.mode; existing rows are custom clips. Matches app/schemas/.../3.json. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `track_settings` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'Custom'")
            }
        }

        /** Adds saved_queue. The SQL matches app/schemas/.../4.json exactly. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_queue` (" +
                        "`position` INTEGER NOT NULL, " +
                        "`media_store_id` INTEGER NOT NULL, " +
                        "`relative_path` TEXT, " +
                        "`display_name` TEXT, " +
                        "`size_bytes` INTEGER NOT NULL, " +
                        "`duration_ms` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`position`))",
                )
            }
        }

        @Volatile
        private var instance: VeyraDatabase? = null

        /** One database per process, built on first use. */
        fun get(context: Context): VeyraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VeyraDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
