package io.github.admilsonlandim.veyra.data.media

import kotlinx.coroutines.flow.Flow

/** Source of the device's music tracks. An interface only so the ViewModel can be tested with a fake. */
interface TrackRepository {
    /** Emits the current tracks, and again whenever the underlying library changes. */
    fun tracks(): Flow<List<Track>>
}
