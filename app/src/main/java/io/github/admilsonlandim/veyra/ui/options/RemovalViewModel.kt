package io.github.admilsonlandim.veyra.ui.options

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
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
import io.github.admilsonlandim.veyra.data.queue.SavedQueueStore
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
 * storage permission -> clean-up and result. Nothing is removed before [confirm].
 *
 * The track waiting for the system dialog or the permission is kept in [savedState], so the
 * answer is still handled when Android killed the app while the dialog was open and recreated it
 * afterwards (the result is then delivered to the new process).
 */
class RemovalViewModel(
    private val remover: TrackFileRemover,
    private val ringtonesUsing: suspend (Track) -> Set<RingtoneTarget>,
    private val cleanUp: suspend (Track, RemovalMode) -> Unit,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _confirmation = MutableStateFlow<RemovalConfirmation?>(null)
    val confirmation: StateFlow<RemovalConfirmation?> = _confirmation.asStateFlow()

    private val _events = Channel<RemovalEvent>(Channel.BUFFERED)
    val events: Flow<RemovalEvent> = _events.receiveAsFlow()

    // The track waiting for the system dialog or the permission: the one in memory, or, after the
    // process was recreated, the one rebuilt from the saved state.
    private var pendingInMemory: Track? = null
    private var pending: Track?
        get() = pendingInMemory ?: savedState.get<Long>(KEY_ID)?.let { id ->
            Track(
                id = id,
                contentUri = savedState[KEY_URI] ?: return null,
                title = savedState[KEY_TITLE],
                artist = null,
                album = null,
                albumId = 0,
                durationMs = savedState[KEY_DURATION] ?: 0,
                displayName = savedState[KEY_NAME],
                relativePath = savedState[KEY_PATH],
                sizeBytes = savedState[KEY_SIZE] ?: 0,
                dateModifiedSeconds = 0,
            )
        }
        set(track) {
            pendingInMemory = track
            // Only what removal and clean-up use: the URI, and the TrackKey's fields (plus the
            // title for the message).
            savedState[KEY_ID] = track?.id
            savedState[KEY_URI] = track?.contentUri
            savedState[KEY_TITLE] = track?.title
            savedState[KEY_DURATION] = track?.durationMs
            savedState[KEY_NAME] = track?.displayName
            savedState[KEY_PATH] = track?.relativePath
            savedState[KEY_SIZE] = track?.sizeBytes
        }

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
        private const val KEY_ID = "removal.pending.id"
        private const val KEY_URI = "removal.pending.uri"
        private const val KEY_TITLE = "removal.pending.title"
        private const val KEY_DURATION = "removal.pending.duration"
        private const val KEY_NAME = "removal.pending.name"
        private const val KEY_PATH = "removal.pending.path"
        private const val KEY_SIZE = "removal.pending.size"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val db = VeyraDatabase.get(app)
                val cleaner = TrackReferenceCleaner(
                    playlists = db.playlistDao(),
                    settings = db.trackSettingsDao(),
                    updateSavedQueue = SavedQueueStore.get(app)::update,
                )
                RemovalViewModel(
                    remover = TrackRemover(app),
                    ringtonesUsing = { RingtoneSetter.targetsUsing(app, it) },
                    cleanUp = cleaner::clean,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
