package io.github.admilsonlandim.veyra.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.admilsonlandim.veyra.data.audio.BASS_STRENGTH_MAX
import io.github.admilsonlandim.veyra.data.audio.CUSTOM_PRESET
import io.github.admilsonlandim.veyra.data.audio.EqualizerSettings
import io.github.admilsonlandim.veyra.data.audio.decodeLevels
import io.github.admilsonlandim.veyra.data.audio.encodeLevels
import io.github.admilsonlandim.veyra.data.clips.GlobalClipRule
import io.github.admilsonlandim.veyra.data.media.LibraryView
import io.github.admilsonlandim.veyra.data.media.TrackSort
import io.github.admilsonlandim.veyra.playback.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** Show titles and artists cleaned from the file name when the tags do not help (display only). */
    val cleanNames: Boolean = true,
    val view: LibraryView = LibraryView.Tracks,
)

data class PlaybackModes(val shuffle: Boolean = false, val repeat: RepeatMode = RepeatMode.Off)

/** Settings > Playback. Each "skip" has its own switch so the chosen seconds are kept when off. */
data class PlaybackPreferences(
    val skipStartEnabled: Boolean = false,
    val skipStartSeconds: Int = DEFAULT_GLOBAL_SKIP_SECONDS,
    val skipEndEnabled: Boolean = false,
    val skipEndSeconds: Int = DEFAULT_GLOBAL_SKIP_SECONDS,
    val skipSilence: Boolean = false,
) {
    val globalRule: GlobalClipRule
        get() = GlobalClipRule(
            skipStartMs = if (skipStartEnabled) skipStartSeconds * 1_000L else 0,
            skipEndMs = if (skipEndEnabled) skipEndSeconds * 1_000L else 0,
        )
}

const val DEFAULT_GLOBAL_SKIP_SECONDS = 10

/** Settings > Appearance. */
enum class AppearanceMode { System, Dark, Light }

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
                cleanNames = prefs[KEY_CLEAN_NAMES] ?: true,
                view = prefs[KEY_VIEW]?.let { name -> LibraryView.entries.firstOrNull { it.name == name } }
                    ?: LibraryView.Tracks,
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

    val playback: Flow<PlaybackPreferences> = store.data
        .map { prefs ->
            PlaybackPreferences(
                skipStartEnabled = prefs[KEY_SKIP_START_ON] ?: false,
                skipStartSeconds = prefs[KEY_SKIP_START_S] ?: DEFAULT_GLOBAL_SKIP_SECONDS,
                skipEndEnabled = prefs[KEY_SKIP_END_ON] ?: false,
                skipEndSeconds = prefs[KEY_SKIP_END_S] ?: DEFAULT_GLOBAL_SKIP_SECONDS,
                skipSilence = prefs[KEY_SKIP_SILENCE] ?: false,
            )
        }
        .distinctUntilChanged()

    val equalizer: Flow<EqualizerSettings> = store.data
        .map { prefs ->
            EqualizerSettings(
                enabled = prefs[KEY_EQ_ON] ?: false,
                preset = prefs[KEY_EQ_PRESET] ?: CUSTOM_PRESET,
                bandLevels = decodeLevels(prefs[KEY_EQ_LEVELS]),
                bassBoostEnabled = prefs[KEY_BASS_ON] ?: false,
                bassStrength = (prefs[KEY_BASS_STRENGTH] ?: 500).coerceIn(0, BASS_STRENGTH_MAX),
            )
        }
        .distinctUntilChanged()

    suspend fun setPlayback(playback: PlaybackPreferences) = store.edit {
        it[KEY_SKIP_START_ON] = playback.skipStartEnabled
        it[KEY_SKIP_START_S] = playback.skipStartSeconds
        it[KEY_SKIP_END_ON] = playback.skipEndEnabled
        it[KEY_SKIP_END_S] = playback.skipEndSeconds
        it[KEY_SKIP_SILENCE] = playback.skipSilence
    }

    suspend fun setEqualizer(settings: EqualizerSettings) = store.edit {
        it[KEY_EQ_ON] = settings.enabled
        it[KEY_EQ_PRESET] = settings.preset
        it[KEY_EQ_LEVELS] = encodeLevels(settings.bandLevels)
        it[KEY_BASS_ON] = settings.bassBoostEnabled
        it[KEY_BASS_STRENGTH] = settings.bassStrength
    }

    val appearance: Flow<AppearanceMode> = store.data
        .map { prefs -> AppearanceMode.entries.firstOrNull { it.name == prefs[KEY_APPEARANCE] } ?: AppearanceMode.System }
        .distinctUntilChanged()

    suspend fun setAppearance(mode: AppearanceMode) = store.edit { it[KEY_APPEARANCE] = mode.name }

    /** Current item and position in the file of the last queue (the items are in Room). */
    suspend fun queuePosition(): Pair<Int, Long> = store.data.map { prefs ->
        (prefs[KEY_QUEUE_INDEX] ?: 0) to (prefs[KEY_QUEUE_POSITION] ?: 0L)
    }.first()

    suspend fun setQueuePosition(index: Int, positionMs: Long) = store.edit {
        it[KEY_QUEUE_INDEX] = index
        it[KEY_QUEUE_POSITION] = positionMs
    }

    suspend fun setSort(sort: TrackSort) = store.edit { it[KEY_SORT] = sort.name }

    suspend fun setExcludedFolders(folders: Set<String>) = store.edit { it[KEY_EXCLUDED_FOLDERS] = folders }

    suspend fun setSkipShortTracks(enabled: Boolean) = store.edit { it[KEY_SKIP_SHORT] = enabled }

    suspend fun setMinDurationSeconds(seconds: Int) = store.edit { it[KEY_MIN_DURATION] = seconds }

    suspend fun setUseDeviceImages(enabled: Boolean) = store.edit { it[KEY_DEVICE_IMAGES] = enabled }

    suspend fun setCleanNames(enabled: Boolean) = store.edit { it[KEY_CLEAN_NAMES] = enabled }

    suspend fun setLibraryView(view: LibraryView) = store.edit { it[KEY_VIEW] = view.name }

    suspend fun setPlaybackModes(modes: PlaybackModes) = store.edit {
        it[KEY_SHUFFLE] = modes.shuffle
        it[KEY_REPEAT] = modes.repeat.name
    }

    companion object {
        private val KEY_SORT = stringPreferencesKey("library_sort")
        private val KEY_APPEARANCE = stringPreferencesKey("appearance")
        private val KEY_QUEUE_INDEX = intPreferencesKey("queue_index")
        private val KEY_QUEUE_POSITION = longPreferencesKey("queue_position_ms")
        private val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("library_excluded_folders")
        private val KEY_SKIP_SHORT = booleanPreferencesKey("library_skip_short")
        private val KEY_MIN_DURATION = intPreferencesKey("library_min_duration_s")
        private val KEY_DEVICE_IMAGES = booleanPreferencesKey("artwork_device_images")
        private val KEY_CLEAN_NAMES = booleanPreferencesKey("library_clean_names")
        private val KEY_VIEW = stringPreferencesKey("library_view")
        private val KEY_SHUFFLE = booleanPreferencesKey("playback_shuffle")
        private val KEY_REPEAT = stringPreferencesKey("playback_repeat")
        private val KEY_SKIP_START_ON = booleanPreferencesKey("global_skip_start_on")
        private val KEY_SKIP_START_S = intPreferencesKey("global_skip_start_s")
        private val KEY_SKIP_END_ON = booleanPreferencesKey("global_skip_end_on")
        private val KEY_SKIP_END_S = intPreferencesKey("global_skip_end_s")
        private val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val KEY_EQ_ON = booleanPreferencesKey("eq_enabled")
        private val KEY_EQ_PRESET = intPreferencesKey("eq_preset")
        private val KEY_EQ_LEVELS = stringPreferencesKey("eq_band_levels_mb")
        private val KEY_BASS_ON = booleanPreferencesKey("bass_boost_enabled")
        private val KEY_BASS_STRENGTH = intPreferencesKey("bass_boost_strength")

        @Volatile
        private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext.dataStore).also { instance = it }
            }
    }
}
