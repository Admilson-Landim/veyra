package com.example.veyra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App database. Every schema change from version 1 on needs a Migration added to the builder
 * below; destructive fallback is deliberately not used, so user playlists are never wiped.
 * Exported schemas live in app/schemas (one JSON per version).
 */
@Database(
    entities = [PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VeyraDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        private const val NAME = "veyra.db"

        @Volatile
        private var instance: VeyraDatabase? = null

        /** One database per process, built on first use. */
        fun get(context: Context): VeyraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VeyraDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
