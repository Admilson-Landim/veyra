package io.github.admilsonlandim.veyra.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How setting a system sound ended. */
sealed interface RingtoneResult {
    data class Set(val track: Track, val target: RingtoneTarget) : RingtoneResult
    data object Failed : RingtoneResult

    /** The user stopped it while the excerpt was being created: nothing was changed. */
    data object Cancelled : RingtoneResult
}

/**
 * Sets a track (or an excerpt of it) as a system sound. Creating an excerpt takes a few seconds,
 * so the work runs here, in [viewModelScope]: it keeps going through rotation and other
 * configuration changes, which recreate the screen but not this ViewModel. Only the user stops
 * it ([cancel]), and that is always reported.
 */
class RingtoneViewModel(
    private val setSound: suspend (Track, RingtoneTarget, TrackClip?) -> Boolean,
) : ViewModel() {

    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    private val _results = Channel<RingtoneResult>(Channel.BUFFERED)
    val results: Flow<RingtoneResult> = _results.receiveAsFlow()

    private var job: Job? = null

    /** Starts setting [track] as the [target] sound; ignored while another one is in progress. */
    fun set(track: Track, target: RingtoneTarget, excerpt: TrackClip?) {
        if (job?.isActive == true) return
        _inProgress.value = true
        job = viewModelScope.launch {
            val result = try {
                if (setSound(track, target, excerpt)) RingtoneResult.Set(track, target) else RingtoneResult.Failed
            } catch (e: CancellationException) {
                withContext(NonCancellable) { _results.send(RingtoneResult.Cancelled) }
                throw e
            } finally {
                _inProgress.value = false
            }
            _results.send(result)
        }
    }

    /** Stops the sound being created; the exporter removes any partial file. */
    fun cancel() {
        job?.cancel()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                RingtoneViewModel { track, target, excerpt -> RingtoneSetter.set(app, track, target, excerpt) }
            }
        }
    }
}
