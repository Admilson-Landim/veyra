package io.github.admilsonlandim.veyra.ui.options

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.admilsonlandim.veyra.data.db.VeyraDatabase
import io.github.admilsonlandim.veyra.data.media.RemovalFailure
import io.github.admilsonlandim.veyra.data.media.RemovalMode
import io.github.admilsonlandim.veyra.data.media.RemovalStep
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackFileRemover
import io.github.admilsonlandim.veyra.data.media.TrackReferenceCleaner
import io.github.admilsonlandim.veyra.data.media.TrackRemover
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The app's confirmation dialog for [track]; [ringtones] are the system sounds set to its file. */
data class RemovalConfirmation(val track: Track, val mode: RemovalMode, val ringtones: Set<RingtoneTarget>)

/** What the screen must do next. Cancelling the system dialog produces none: it is not an error. */
sealed interface RemovalEvent {
    data class AskSystem(val intentSender: IntentSender) : RemovalEvent
    data object AskWritePermission : RemovalEvent
    data class Removed(val track: Track, val mode: RemovalMode) : RemovalEvent
    data class Failed(val reason: RemovalFailure) : RemovalEvent
}

/**
 * Removing a track's file: request -> the app's confirmation -> the system's confirmation or the
 * storage permission -> clean-up and result. Nothing is removed before [confirm]. The track
 * waiting for the system survives configuration changes here, unlike in the UI.
 */
class RemovalViewModel(
    private val remover: TrackFileRemover,
    private val ringtonesUsing: suspend (Track) -> Set<RingtoneTarget>,
    private val cleanUp: suspend (Track, RemovalMode) -> Unit,
) : ViewModel() {

    private val _confirmation = MutableStateFlow<RemovalConfirmation?>(null)
    val confirmation: StateFlow<RemovalConfirmation?> = _confirmation.asStateFlow()

    private val _events = Channel<RemovalEvent>(Channel.BUFFERED)
    val events: Flow<RemovalEvent> = _events.receiveAsFlow()

    // The track waiting for the system dialog or the permission.
    private var pending: Track? = null

    fun request(track: Track) {
        viewModelScope.launch {
            _confirmation.value = RemovalConfirmation(track, remover.mode, ringtonesUsing(track))
        }
    }

    fun dismiss() {
        _confirmation.value = null
    }

    fun confirm() {
        val track = _confirmation.value?.track ?: return
        _confirmation.value = null
        viewModelScope.launch { follow(track, remover.start(track)) }
    }

    fun onSystemResult(confirmed: Boolean) {
        val track = takePending() ?: return
        if (confirmed) viewModelScope.launch { follow(track, remover.onSystemConfirmed(track)) }
    }

    fun onPermissionResult(granted: Boolean) {
        val track = takePending() ?: return
        viewModelScope.launch {
            if (granted) follow(track, remover.start(track)) else _events.send(RemovalEvent.Failed(RemovalFailure.NoPermission))
        }
    }

    private fun takePending(): Track? = pending.also { pending = null }

    private suspend fun follow(track: Track, step: RemovalStep) {
        when (step) {
            RemovalStep.Removed -> {
                cleanUp(track, remover.mode)
                _events.send(RemovalEvent.Removed(track, remover.mode))
            }
            is RemovalStep.NeedsSystemConfirmation -> {
                pending = track
                _events.send(RemovalEvent.AskSystem(step.intentSender))
            }
            RemovalStep.NeedsWritePermission -> {
                pending = track
                _events.send(RemovalEvent.AskWritePermission)
            }
            is RemovalStep.Failed -> _events.send(RemovalEvent.Failed(step.reason))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val db = VeyraDatabase.get(app)
                val prefs = AppPreferences.get(app)
                val cleaner = TrackReferenceCleaner(
                    playlists = db.playlistDao(),
                    settings = db.trackSettingsDao(),
                    queue = db.savedQueueDao(),
                    readQueuePosition = prefs::queuePosition,
                    writeQueuePosition = prefs::setQueuePosition,
                )
                RemovalViewModel(
                    remover = TrackRemover(app),
                    ringtonesUsing = { RingtoneSetter.targetsUsing(app, it) },
                    cleanUp = cleaner::clean,
                )
            }
        }
    }
}
