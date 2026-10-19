package com.example.veyra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.veyra.data.media.Track
import com.example.veyra.playback.PlaybackError
import com.example.veyra.playback.PlayerConnection
import com.example.veyra.playback.PlayerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the [PlayerConnection] for the Activity: survives rotation, so the MediaController is
 * built once and released in [onCleared].
 */
class PlayerViewModel(private val connection: PlayerConnection) : ViewModel() {

    init {
        connection.connect()
    }

    // WhileSubscribed with no timeout: the position ticker stops as soon as the UI
    // stops collecting (app in the background), and restarts when it is visible again.
    val state: StateFlow<PlayerState> =
        connection.stateWithProgress()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), connection.state.value)

    val errors: SharedFlow<PlaybackError> = connection.errors

    fun play(tracks: List<Track>, startIndex: Int) = connection.playQueue(tracks, startIndex)
    fun togglePlayPause() = connection.togglePlayPause()
    fun skipToNext() = connection.skipToNext()
    fun skipToPrevious() = connection.skipToPrevious()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun toggleShuffle() = connection.toggleShuffle()

    override fun onCleared() {
        connection.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(PlayerConnection(this[APPLICATION_KEY]!!)) }
        }
    }
}
