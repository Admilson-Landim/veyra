package com.example.veyra.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.veyra.data.media.TrackSort
import com.example.veyra.playback.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Library display and filtering preferences. */
data class LibraryPreferences(
    val sort: TrackSort = TrackSort.Title,
    /** Folders (relative paths such as "Recordings/") whose tracks are hidden from the library. */
    val excludedFolders: Set<String> = DEFAULT_EXCLUDED_FOLDERS,
    val skipShortTracks: Boolean = false,
    val minDurationSeconds: Int = DEFAULT_MIN_DURATION_SECONDS,
    /**
     * Look for covers stored as separate images (same name, folder cover, VidMate thumbnail)
     * when a file has none embedded. On Android 13+ it also needs the photos permission.
     */
    val useDeviceImages: Boolean = true,
)

data class PlaybackModes(val shuffle: Boolean = false, val repeat: RepeatMode = RepeatMode.Off)

/**
 * Folders excluded by default. They hold voice recordings and voice notes, which MediaStore
 * often flags as music:
 * - "Recordings/": standard recordings folder (Android 12+) and Samsung's Voice Recorder.
 * - "MIUI/sound_recorder/": Xiaomi's recorder.
 * - "Sounds/CallRecord/": Huawei/Honor call recordings (their voice recorder uses "Sounds/").
 * - WhatsApp voice notes, at the legacy location and the Android 11+ scoped-storage one.
 * "WhatsApp Audio" is not excluded: people do receive music that way.
 */
val DEFAULT_EXCLUDED_FOLDERS: Set<String> = setOf(
    "Recordings/",
    "MIUI/sound_recorder/",
    "Sounds/CallRecord/",
    "WhatsApp/Media/WhatsApp Voice Notes/",
    "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/",
)

const val DEFAULT_MIN_DURATION_SECONDS = 30

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App preferences, backed by DataStore: a handful of key/value settings read as Flows.
 * Shared by later phases, which add their own keys here.
 */
class AppPreferences private constructor(private val store: DataStore<Preferences>) {

    val library: Flow<LibraryPreferences> = store.data
        .map { prefs ->
            LibraryPreferences(
                sort = prefs[KEY_SORT]?.let { name -> TrackSort.entries.firstOrNull { it.name == name } }
                    ?: TrackSort.Title,
                excludedFolders = prefs[KEY_EXCLUDED_FOLDERS] ?: DEFAULT_EXCLUDED_FOLDERS,
                skipShortTracks = prefs[KEY_SKIP_SHORT] ?: false,
                minDurationSeconds = prefs[KEY_MIN_DURATION] ?: DEFAULT_MIN_DURATION_SECONDS,
                useDeviceImages = prefs[KEY_DEVICE_IMAGES] ?: true,
            )
        }
        .distinctUntilChanged()

    val playbackModes: Flow<PlaybackModes> = store.data
        .map { prefs ->
            PlaybackModes(
                shuffle = prefs[KEY_SHUFFLE] ?: false,
                repeat = prefs[KEY_REPEAT]?.let { name -> RepeatMode.entries.firstOrNull { it.name == name } }
                    ?: RepeatMode.Off,
            )
        }
        .distinctUntilChanged()

    suspend fun setSort(sort: TrackSort) = store.edit { it[KEY_SORT] = sort.name }

    suspend fun setExcludedFolders(folders: Set<String>) = store.edit { it[KEY_EXCLUDED_FOLDERS] = folders }

    suspend fun setSkipShortTracks(enabled: Boolean) = store.edit { it[KEY_SKIP_SHORT] = enabled }

    suspend fun setMinDurationSeconds(seconds: Int) = store.edit { it[KEY_MIN_DURATION] = seconds }

    suspend fun setUseDeviceImages(enabled: Boolean) = store.edit { it[KEY_DEVICE_IMAGES] = enabled }

    suspend fun setPlaybackModes(modes: PlaybackModes) = store.edit {
        it[KEY_SHUFFLE] = modes.shuffle
        it[KEY_REPEAT] = modes.repeat.name
    }

    companion object {
        private val KEY_SORT = stringPreferencesKey("library_sort")
        private val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("library_excluded_folders")
        private val KEY_SKIP_SHORT = booleanPreferencesKey("library_skip_short")
        private val KEY_MIN_DURATION = intPreferencesKey("library_min_duration_s")
        private val KEY_DEVICE_IMAGES = booleanPreferencesKey("artwork_device_images")
        private val KEY_SHUFFLE = booleanPreferencesKey("playback_shuffle")
        private val KEY_REPEAT = stringPreferencesKey("playback_repeat")

        @Volatile
        private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext.dataStore).also { instance = it }
            }
    }
}
