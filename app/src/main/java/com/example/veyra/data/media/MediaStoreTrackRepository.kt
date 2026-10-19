package com.example.veyra.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/** Reads music from MediaStore.Audio and re-queries whenever MediaStore reports a change. */
class MediaStoreTrackRepository(
    private val resolver: ContentResolver,
) : TrackRepository {

    private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun tracks(): Flow<List<Track>> =
        libraryChanges()
            // MediaProvider can notify before a deletion is visible to queries (observed on
            // Android 10: the first of two notifications still returned the deleted row), and a
            // scan sends bursts. Waiting briefly after the last change avoids reading stale data.
            .debounce(CHANGE_DEBOUNCE_MS)
            .onStart { emit(Unit) }
            // Keep only the latest pending change, and cancel an in-flight query for a newer one.
            .conflate()
            .mapLatest { queryTracks() }
            .flowOn(Dispatchers.IO)

    /** Emits on every MediaStore audio change, while collected. */
    private fun libraryChanges(): Flow<Unit> = callbackFlow {
        // A null Handler delivers onChange on a binder thread; trySend is thread-safe.
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun queryTracks(): List<Track> {
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        // Before Android 10 there is no RELATIVE_PATH: derive the folder from the file path.
        @Suppress("DEPRECATION")
        val dataColumn = MediaStore.Audio.Media.DATA
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (hasRelativePath) add(MediaStore.Audio.Media.RELATIVE_PATH) else add(dataColumn)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DATE_ADDED)
        }.toTypedArray()
        val selection = buildSelection()

        val cursor = resolver.query(collection, projection, selection, null, null)
            ?: error("MediaStore query returned no cursor")

        return cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativePathCol = it.getColumnIndexOrThrow(
                if (hasRelativePath) MediaStore.Audio.Media.RELATIVE_PATH else dataColumn,
            )
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateModifiedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            buildList(it.count) {
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val displayName = it.getString(displayNameCol)
                    add(
                        Track(
                            id = id,
                            contentUri = ContentUris.withAppendedId(collection, id).toString(),
                            title = resolveTitle(it.getString(titleCol), displayName),
                            artist = cleanTag(it.getString(artistCol)),
                            album = cleanTag(it.getString(albumCol)),
                            albumId = it.getLong(albumIdCol),
                            durationMs = it.getLong(durationCol),
                            displayName = displayName,
                            relativePath = it.getString(relativePathCol)?.let { value ->
                                if (hasRelativePath) value else relativeFolderFromPath(value)
                            },
                            sizeBytes = it.getLong(sizeCol),
                            dateModifiedSeconds = it.getLong(dateModifiedCol),
                            dateAddedSeconds = it.getLong(dateAddedCol),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Music only, and never ringtones, notification or alarm sounds, podcasts or (Android 12+)
     * recordings, even when they are also flagged as music. Folder and duration filters are user
     * preferences and are applied later, without re-querying.
     */
    private fun buildSelection(): String = buildList {
        add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        add("${MediaStore.Audio.Media.DURATION} > 0")
        add("${MediaStore.Audio.Media.IS_RINGTONE} = 0")
        add("${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
        add("${MediaStore.Audio.Media.IS_ALARM} = 0")
        // NULL on files indexed before the column existed: treat as "not a podcast".
        add("IFNULL(${MediaStore.Audio.Media.IS_PODCAST}, 0) = 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("IFNULL(${MediaStore.Audio.Media.IS_RECORDING}, 0) = 0")
        }
    }.joinToString(" AND ")

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 300L
    }
}
