package com.example.veyra.playback

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors

/**
 * Session bitmap loader for the notification and lock screen. Embedded covers are decoded by
 * [delegate] (Media3's loader, on its background executor). When the file has none, [fallback]
 * looks for a cover stored as a separate image (run on [fallbackExecutor], never the main
 * thread); if that finds nothing too, or decoding fails, the default Veyra artwork is shown.
 *
 * The default cannot go in MediaItem.artworkUri: ExoPlayer would then drop the embedded
 * artworkData, since MediaItem metadata replaces artwork URI and data together.
 */
@OptIn(UnstableApi::class)
class DefaultArtworkBitmapLoader(
    private val delegate: BitmapLoader,
    private val defaultArtwork: () -> Bitmap,
    private val fallback: (MediaMetadata) -> Bitmap? = { null },
    private val fallbackExecutor: ListeningExecutorService,
) : BitmapLoader by delegate {

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        val future = delegate.loadBitmapFromMetadata(metadata)
            ?: fallbackExecutor.submit<Bitmap> { runCatching { fallback(metadata) }.getOrNull() ?: defaultArtwork() }
        return Futures.catching(future, Exception::class.java, { defaultArtwork() }, MoreExecutors.directExecutor())
    }
}
