package io.github.admilsonlandim.veyra.ui.options

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.media.RemovalMode
import io.github.admilsonlandim.veyra.data.media.RemovalStep
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackRemover
import kotlinx.coroutines.launch

/**
 * Removes [request] from the device: the app's confirmation ([RemoveTrackDialog]), then the
 * system's (or the storage permission on Android 10 and older), then [onRemoved].
 *
 * Cancelling the system dialog leaves the file alone, silently: the user chose not to.
 *
 * @param trackForId finds the pending track again if the activity was recreated while the
 *   system dialog was open.
 */
@Composable
fun TrackRemovalHost(
    request: Track?,
    onDismissRequest: () -> Unit,
    trackForId: (Long) -> Track?,
    onRemoved: (Track) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val remover = remember(context) { TrackRemover(context) }
    val scope = rememberCoroutineScope()
    // The track waiting for the system dialog or the permission.
    var pending by remember { mutableStateOf<Track?>(null) }
    var pendingId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun takePending(): Track? = (pending ?: pendingId?.let(trackForId)).also {
        pending = null
        pendingId = null
    }

    // Declared before its body: the launchers call it, and it launches them.
    lateinit var follow: (Track, RemovalStep) -> Unit

    val systemConfirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val track = takePending() ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) scope.launch { follow(track, remover.onSystemConfirmed(track)) }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val track = takePending() ?: return@rememberLauncherForActivityResult
        if (granted) {
            scope.launch { follow(track, remover.start(track)) }
        } else {
            onMessage(resources.getString(R.string.remove_permission_denied))
        }
    }

    follow = { track, step ->
        when (step) {
            RemovalStep.Removed -> {
                onRemoved(track)
                val name = track.title ?: track.displayName.orEmpty()
                val done = if (remover.mode == RemovalMode.Trash) R.string.remove_done_trash else R.string.remove_done_delete
                onMessage(resources.getString(done, name))
            }
            is RemovalStep.NeedsSystemConfirmation -> {
                pending = track
                pendingId = track.id
                systemConfirmation.launch(IntentSenderRequest.Builder(step.intentSender).build())
            }
            RemovalStep.NeedsWritePermission -> {
                pending = track
                pendingId = track.id
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            RemovalStep.Failed -> onMessage(resources.getString(R.string.remove_failed))
        }
    }

    if (request != null) {
        RemoveTrackDialog(
            track = request,
            mode = remover.mode,
            onConfirm = {
                onDismissRequest()
                scope.launch { follow(request, remover.start(request)) }
            },
            onDismiss = onDismissRequest,
        )
    }
}
