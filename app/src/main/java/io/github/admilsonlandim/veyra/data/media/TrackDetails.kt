package io.github.admilsonlandim.veyra.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the "Properties" sheet shows. Null fields are unknown. */
data class TrackDetails(
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: String?,
    val genre: String?,
    val trackNumber: String?,
    val durationMs: Long,
    val mimeType: String?,
    val bitrateBps: Int?,
    val sampleRateHz: Int?,
    val sizeBytes: Long,
    /** Folder and file name, e.g. "Music/Album/song.mp3". */
    val path: String?,
    val dateAddedSeconds: Long,
)

/**
 * Reads tags and technical data that MediaStore does not expose consistently, with
 * MediaMetadataRetriever (and MediaExtractor for the sample rate before Android 12), on IO.
 * Values MediaStore already has (title, artist, album, size, path, date) come from the [Track].
 */
class TrackDetailsLoader(context: Context) {

    private val appContext = context.applicationContext

    suspend fun load(track: Track): TrackDetails = withContext(Dispatchers.IO) {
        val uri = track.contentUri.toUri()
        val retriever = MediaMetadataRetriever()
        try {
            runCatching { retriever.setDataSource(appContext, uri) }
            fun meta(key: Int): String? = runCatching { cleanTag(retriever.extractMetadata(key)) }.getOrNull()

            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                meta(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } else {
                null
            } ?: extractorSampleRate(track)

            TrackDetails(
                title = track.title,
                artist = track.artist,
                album = track.album,
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: meta(MediaMetadataRetriever.METADATA_KEY_DATE),
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                durationMs = track.durationMs,
                mimeType = meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                bitrateBps = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                sampleRateHz = sampleRate,
                sizeBytes = track.sizeBytes,
                path = listOfNotNull(track.relativePath, track.displayName).joinToString("").ifEmpty { null },
                dateAddedSeconds = track.dateAddedSeconds,
            )
        } finally {
            retriever.release()
        }
    }

    private fun extractorSampleRate(track: Track): Int? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, track.contentUri.toUri(), null)
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }
                ?.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
}
