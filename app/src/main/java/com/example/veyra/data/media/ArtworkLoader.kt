package com.example.veyra.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The two sizes artwork is decoded at; each has its own cache entry. */
enum class ArtworkSize(val px: Int) {
    /** List rows and mini-player (48 dp at up to 4x density). */
    Thumbnail(192),

    /** Full-screen player. */
    Large(1024),
}

/**
 * Loads the cover embedded in a local audio file, off the main thread, with an in-memory cache.
 *
 * Order: ContentResolver.loadThumbnail (Android 10+, backed by the system thumbnail cache),
 * then MediaMetadataRetriever.embeddedPicture decoded with inSampleSize. Returns null when the
 * file has no cover; callers show the default Veyra artwork instead.
 */
class ArtworkLoader private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val bitmaps = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // Files known to have no cover, so scrolling back does not re-run MediaMetadataRetriever.
    private val misses = LruCache<String, Boolean>(1024)

    // A fast fling starts many loads; cap concurrent decoding so it cannot starve the device.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)

    /** Synchronous cache lookup, so already-loaded covers appear without a fade. */
    fun cached(contentUri: String, size: ArtworkSize): Bitmap? = bitmaps.get(key(contentUri, size))

    /** Cancellable: if the caller's coroutine is cancelled (item scrolled away), work stops early. */
    suspend fun load(contentUri: String, size: ArtworkSize): Bitmap? {
        val key = key(contentUri, size)
        bitmaps.get(key)?.let { return it }
        if (misses.get(contentUri) != null) return null

        val bitmap = withContext(decodeDispatcher) {
            val uri = contentUri.toUri()
            val context = coroutineContext
            // Lets an in-flight loadThumbnail stop as soon as the coroutine is cancelled.
            val signal = CancellationSignal()
            val handle = context[Job]?.invokeOnCompletion { cause ->
                if (cause is CancellationException) signal.cancel()
            }
            try {
                firstAvailable(
                    { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) loadThumbnail(uri, size, signal) else null },
                    { context.ensureActive(); loadEmbeddedPicture(uri, size) },
                )
            } finally {
                handle?.dispose()
            }
        }
        currentCoroutineContext().ensureActive()
        if (bitmap != null) bitmaps.put(key, bitmap) else misses.put(contentUri, true)
        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadThumbnail(uri: Uri, size: ArtworkSize, signal: CancellationSignal): Bitmap =
        appContext.contentResolver.loadThumbnail(uri, Size(size.px, size.px), signal)

    private fun loadEmbeddedPicture(uri: Uri, size: ArtworkSize): Bitmap? {
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(appContext, uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, size.px)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun key(contentUri: String, size: ArtworkSize) = "${size.name}:$contentUri"

    companion object {
        @Volatile
        private var instance: ArtworkLoader? = null

        /** One loader (and one cache) per process. */
        fun get(context: Context): ArtworkLoader =
            instance ?: synchronized(this) { instance ?: ArtworkLoader(context).also { instance = it } }

        // 1/8 of the app's heap, the usual budget for an in-memory bitmap cache.
        private fun cacheSizeBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).toInt()
    }
}
