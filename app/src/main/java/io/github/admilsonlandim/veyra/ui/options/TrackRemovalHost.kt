package io.github.admilsonlandim.veyra.ui.options

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admilsonlandim.veyra.R
import io.github.admilsonlandim.veyra.data.media.RemovalFailure
import io.github.admilsonlandim.veyra.data.media.RemovalMode
import io.github.admilsonlandim.veyra.data.media.Track

/**
 * Shows [viewModel]'s confirmation and runs what it asks for: the system dialog, the storage
 * permission, then [onRemoved] (drop the track from the player's queue) and a message.
 */
@Composable
fun TrackRemovalHost(
    viewModel: RemovalViewModel,
    onRemoved: (Track) -> Unit,
    onMessage: (String) -> Unit,
) {
    val resources = LocalResources.current
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()

    val systemConfirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onSystemResult(it.resultCode == Activity.RESULT_OK)
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onPermissionResult(it)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RemovalEvent.AskSystem -> systemConfirmation.launch(IntentSenderRequest.Builder(event.intentSender).build())
                RemovalEvent.AskWritePermission -> writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                is RemovalEvent.Removed -> {
                    onRemoved(event.track)
                    val name = event.track.title ?: event.track.displayName.orEmpty()
                    val done = if (event.mode == RemovalMode.Trash) R.string.remove_done_trash else R.string.remove_done_delete
                    onMessage(resources.getString(done, name))
                }
                is RemovalEvent.Failed -> onMessage(
                    resources.getString(
                        when (event.reason) {
                            RemovalFailure.NotFound -> R.string.remove_failed_not_found
                            RemovalFailure.ReadOnly -> R.string.remove_failed_read_only
                            RemovalFailure.NoPermission -> R.string.remove_permission_denied
                            RemovalFailure.Other -> R.string.remove_failed
                        },
                    ),
                )
            }
        }
    }

    confirmation?.let { RemoveTrackDialog(it, onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss) }
}
