package com.example.veyra.ui.options

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.veyra.R
import com.example.veyra.data.media.ArtworkSize
import com.example.veyra.data.media.Track
import com.example.veyra.data.media.TrackDetails
import com.example.veyra.data.media.TrackDetailsLoader
import com.example.veyra.ui.library.formatDuration
import com.example.veyra.ui.theme.components.TrackArtwork

/** What the track options sheet can do with its track. */
data class TrackOptionsActions(
    val onAddToPlaylist: (Track) -> Unit,
    val onAdjustClip: (Track) -> Unit,
    val onProperties: (Track) -> Unit,
    val onSetRingtone: (Track, RingtoneTarget) -> Unit,
)

/** Options for one track, opened by long-press or the "more options" button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    actions: TrackOptionsActions,
    onDismiss: () -> Unit,
) {
    var ringtoneExpanded by rememberSaveable { mutableStateOf(false) }
    // Each option closes the sheet first, then acts.
    fun pick(action: () -> Unit) {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            ) {
                TrackArtwork(contentUri = track.contentUri, size = 48.dp, artworkSize = ArtworkSize.Thumbnail)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title ?: stringResource(R.string.track_unknown_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = track.artist ?: stringResource(R.string.track_unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OptionRow(R.drawable.ic_playlist_add, stringResource(R.string.options_add_to_playlist)) {
                pick { actions.onAddToPlaylist(track) }
            }
            OptionRow(R.drawable.ic_content_cut, stringResource(R.string.clip_adjust)) {
                pick { actions.onAdjustClip(track) }
            }
            OptionRow(R.drawable.ic_info, stringResource(R.string.options_properties)) {
                pick { actions.onProperties(track) }
            }
            OptionRow(R.drawable.ic_ringtone, stringResource(R.string.options_set_ringtone)) {
                ringtoneExpanded = !ringtoneExpanded
            }
            AnimatedVisibility(visible = ringtoneExpanded) {
                Column(Modifier.padding(start = 40.dp)) {
                    Text(
                        text = stringResource(R.string.ringtone_full_file_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    RingtoneTarget.entries.forEach { target ->
                        OptionRow(icon = null, label = stringResource(target.labelRes())) {
                            pick { actions.onSetRingtone(track, target) }
                        }
                    }
                }
            }
        }
    }
}

fun RingtoneTarget.labelRes(): Int = when (this) {
    RingtoneTarget.Call -> R.string.ringtone_call
    RingtoneTarget.Notification -> R.string.ringtone_notification
    RingtoneTarget.Alarm -> R.string.ringtone_alarm
}

@Composable
private fun OptionRow(@DrawableRes icon: Int?, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(24.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Read-only technical and tag information, loaded off the main thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPropertiesSheet(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loader = remember(context) { TrackDetailsLoader(context) }
    val details by produceState<TrackDetails?>(initialValue = null, track) { value = loader.load(track) }
    val unknown = stringResource(R.string.properties_unknown)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.properties_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .semantics { heading() },
            )
            val d = details
            // Formats numbers and dates in the app's language, which may differ from the system's.
            val locale = LocalConfiguration.current.locales[0]
            if (d == null) {
                Text(stringResource(R.string.properties_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val rows = listOf(
                    R.string.prop_title to d.title,
                    R.string.prop_artist to d.artist,
                    R.string.prop_album to d.album,
                    R.string.prop_year to formatYear(d.year),
                    R.string.prop_genre to d.genre,
                    R.string.prop_track_number to formatTrackNumber(d.trackNumber),
                    R.string.prop_duration to d.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                    R.string.prop_format to formatMimeType(d.mimeType),
                    R.string.prop_bitrate to formatBitrate(d.bitrateBps),
                    R.string.prop_sample_rate to formatSampleRate(d.sampleRateHz, locale),
                    R.string.prop_size to formatFileSize(d.sizeBytes, locale),
                    R.string.prop_path to d.path,
                    R.string.prop_date_added to formatDateAdded(d.dateAddedSeconds, locale),
                )
                rows.forEach { (label, value) -> PropertyRow(stringResource(label), value ?: unknown) }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
