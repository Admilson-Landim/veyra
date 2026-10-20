package io.github.admilsonlandim.veyra.ui.theme.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.media.ArtworkLoader
import io.github.admilsonlandim.veyra.data.media.ArtworkSize
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme

/**
 * Square cover for a track: the default Veyra artwork, cross-fading to the embedded cover
 * once it has loaded. The box size never changes, so loading causes no layout jump.
 * Decorative: the surrounding row or screen already announces the track.
 *
 * @param contentUri the track's content:// URI, or null to always show the default artwork.
 */
@Composable
fun TrackArtwork(
    contentUri: String?,
    size: Dp,
    artworkSize: ArtworkSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loader = remember(context) { ArtworkLoader.get(context) }
    // Reloads when the loader's sources change (new images, setting toggled).
    val generation by loader.generation.collectAsState()
    // Leaving the composition (e.g. the row scrolls off screen) cancels the load.
    val bitmap by produceState(
        initialValue = contentUri?.let { loader.cached(it, artworkSize) },
        contentUri,
        artworkSize,
        generation,
    ) {
        // Reset first, so a new track never shows the previous track's cover while loading.
        value = contentUri?.let { loader.cached(it, artworkSize) }
        if (value == null && contentUri != null) value = loader.load(contentUri, artworkSize)
    }
    ArtworkBox(bitmap, size, artworkSize, modifier)
}

/**
 * Cover for a group of tracks (an artist): the first of [contentUris] that has a cover, tried in
 * order, or the default artwork. Callers pass only the first few tracks, so a group without
 * covers costs a bounded number of lookups (misses are cached by the loader).
 */
@Composable
fun GroupArtwork(
    contentUris: List<String>,
    size: Dp,
    artworkSize: ArtworkSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loader = remember(context) { ArtworkLoader.get(context) }
    val generation by loader.generation.collectAsState()
    val bitmap by produceState(
        initialValue = contentUris.firstNotNullOfOrNull { loader.cached(it, artworkSize) },
        contentUris,
        artworkSize,
        generation,
    ) {
        value = contentUris.firstNotNullOfOrNull { loader.cached(it, artworkSize) }
        if (value == null) {
            for (uri in contentUris) {
                val loaded = loader.load(uri, artworkSize)
                if (loaded != null) {
                    value = loaded
                    break
                }
            }
        }
    }
    ArtworkBox(bitmap, size, artworkSize, modifier)
}

@Composable
private fun ArtworkBox(bitmap: Bitmap?, size: Dp, artworkSize: ArtworkSize, modifier: Modifier) {
    val shape = if (artworkSize == ArtworkSize.Large) MaterialTheme.shapes.large else MaterialTheme.shapes.small

    Box(
        modifier
            .size(size)
            .clip(shape),
    ) {
        Crossfade(targetState = bitmap, label = "artwork") { loaded ->
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DefaultArtwork(Modifier.fillMaxSize())
            }
        }
    }
}

/** The default Veyra cover, used wherever a track has no embedded artwork. */
@Composable
fun DefaultArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.veyra_default_artwork),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun TrackArtworkPreview() {
    VeyraTheme {
        TrackArtwork(contentUri = null, size = 160.dp, artworkSize = ArtworkSize.Large)
    }
}
