package com.example.veyra.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.veyra.data.settings.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The two sizes artwork is decoded at; each has its own cache entry. */
enum class ArtworkSize(val px: Int) {
    /** List rows and mini-player (48 dp at up to 4x density). */
    Thumbnail(192),

    /** Full-screen player. */
    Large(1024),
}

/**
 * Loads a track's cover off the main thread, with an in-memory cache.
 *
 * Sources, in order:
 * 1. Embedded in the file: ContentResolver.loadThumbnail (Android 10+, system thumbnail cache),
 *    then MediaMetadataRetriever.embeddedPicture decoded with inSampleSize.
 * 2. A separate image file found by [SidecarArtworkIndex] (same name, folder cover, VidMate
 *    thumbnail), when the "device images" setting is on and images can be read.
 * Returns null when nothing is found; callers then show the default Veyra artwork.
 */
class ArtworkLoader private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    private val bitmaps = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // Tracks known to have no cover, so scrolling back does not re-run MediaMetadataRetriever.
    private val misses = LruCache<String, Boolean>(1024)

    // A fast fling starts many loads; cap concurrent decoding so it cannot starve the device.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)

    // Device images, indexed on first need and dropped whenever MediaStore images change.
    @Volatile
    private var sidecarIndex: SidecarArtworkIndex? = null
    private var imagesObserverRegistered = false

    private val _generation = MutableStateFlow(0)

    /** Bumped when cached results may be stale (images added, setting changed): UI reloads covers. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** Drops cached results so covers are looked up again. */
    fun invalidate() {
        sidecarIndex = null
        bitmaps.evictAll()
        misses.evictAll()
        _generation.update { it + 1 }
    }

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
                ) ?: run {
                    context.ensureActive()
                    if (deviceImagesEnabled()) firstAvailable({ loadSidecar(uri, size, signal) }) else null
                }
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
        resolver.loadThumbnail(uri, Size(size.px, size.px), signal)

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

    /** The setting is on, and images are readable (Android 13+ needs the photos permission). */
    private suspend fun deviceImagesEnabled(): Boolean {
        if (!AppPreferences.get(appContext).library.first().useDeviceImages) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun loadSidecar(audioUri: Uri, size: ArtworkSize, signal: CancellationSignal): Bitmap? {
        val audioId = runCatching { ContentUris.parseId(audioUri) }.getOrNull() ?: return null
        val (folder, name) = audioLocation(audioId) ?: return null
        val imageId = index().find(audioId, folder, name) ?: return null
        val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
        val image = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(imageUri, Size(size.px, size.px), signal)
        } else {
            decodeStream(imageUri, size)
        } ?: return null
        // Video thumbnails often carry black bars; embedded covers are never trimmed.
        return trimLetterbox(image)
    }

    private fun trimLetterbox(image: Bitmap): Bitmap {
        val w = image.width
        val h = image.height
        val row = IntArray(w)
        val column = IntArray(h)
        val bounds = letterboxBounds(
            width = w,
            height = h,
            isDarkRow = { y -> image.getPixels(row, 0, w, 0, y, w, 1); row.all(::isDark) },
            isDarkColumn = { x -> image.getPixels(column, 0, 1, x, 0, 1, h); column.all(::isDark) },
        )
        if (bounds.width == w && bounds.height == h) return image
        return Bitmap.createBitmap(image, bounds.left, bounds.top, bounds.width, bounds.height)
    }

    // Near-black, with some tolerance for compression noise.
    private fun isDark(argb: Int): Boolean =
        (argb shr 16 and 0xFF) < DARK_LEVEL && (argb shr 8 and 0xFF) < DARK_LEVEL && (argb and 0xFF) < DARK_LEVEL

    /** Folder and file name of an audio file, straight from MediaStore (one row). */
    private fun audioLocation(audioId: Long): Pair<String?, String?>? {
        val folderColumn = folderColumn()
        val projection = arrayOf(folderColumn, MediaStore.MediaColumns.DISPLAY_NAME)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
        return resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            folderOf(c.getString(0)) to c.getString(1)
        }
    }

    private fun index(): SidecarArtworkIndex {
        sidecarIndex?.let { return it }
        synchronized(this) {
            sidecarIndex?.let { return it }
            registerImagesObserver()
            val projection = arrayOf(MediaStore.Images.Media._ID, folderColumn(), MediaStore.Images.Media.DISPLAY_NAME)
            val images = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
                ?.use { c ->
                    buildList(c.count) {
                        while (c.moveToNext()) {
                            val folder = folderOf(c.getString(1)) ?: continue
                            val name = c.getString(2) ?: continue
                            add(ImageEntry(c.getLong(0), folder, name))
                        }
                    }
                }.orEmpty()
            return SidecarArtworkIndex(images).also { sidecarIndex = it }
        }
    }

    private fun registerImagesObserver() {
        if (imagesObserverRegistered) return
        imagesObserverRegistered = true
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) = invalidate()
            },
        )
    }

    private fun decodeStream(uri: Uri, size: ArtworkSize): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, size.px)
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    // RELATIVE_PATH exists from Android 10; before that the folder comes from the file path.
    @Suppress("DEPRECATION")
    private fun folderColumn(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA

    private fun folderOf(value: String?): String? = when {
        value == null -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> value
        else -> relativeFolderFromPath(value)
    }

    private fun key(contentUri: String, size: ArtworkSize) = "${size.name}:$contentUri"

    companion object {
        @Volatile
        private var instance: ArtworkLoader? = null

        /** One loader (and one cache) per process. */
        fun get(context: Context): ArtworkLoader =
            instance ?: synchronized(this) { instance ?: ArtworkLoader(context).also { instance = it } }

        private const val DARK_LEVEL = 24

        // 1/8 of the app's heap, the usual budget for an in-memory bitmap cache.
        private fun cacheSizeBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).toInt()
    }
}
