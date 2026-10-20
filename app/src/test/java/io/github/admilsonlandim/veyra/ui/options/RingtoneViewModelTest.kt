package io.github.admilsonlandim.veyra.ui.options

import io.github.admilsonlandim.veyra.data.clips.TrackClip
import io.github.admilsonlandim.veyra.data.media.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingtoneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val track = Track(
        id = 3, contentUri = "content://media/external/audio/media/3", title = "Calm Down", artist = "Rema",
        album = null, albumId = 0, durationMs = 200_000, displayName = "calm.mp3", relativePath = "Music/",
        sizeBytes = 1, dateModifiedSeconds = 0,
    )
    private val excerpt = TrackClip(10_000, 40_000)
    private val results = mutableListOf<RingtoneResult>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(setSound: suspend (Track, RingtoneTarget, TrackClip?) -> Boolean) =
        RingtoneViewModel(setSound).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.results.collect { results += it } }
        }

    @Test
    fun set_reportsSuccess_andShowsProgressMeanwhile() = runTest(dispatcher) {
        val finish = CompletableDeferred<Boolean>()
        val vm = viewModel { _, _, _ -> finish.await() }
        vm.set(track, RingtoneTarget.Call, excerpt)
        advanceUntilIdle()
        assertTrue(vm.inProgress.value)
        finish.complete(true)
        advanceUntilIdle()
        assertFalse(vm.inProgress.value)
        assertEquals(listOf(RingtoneResult.Set(track, RingtoneTarget.Call)), results)
    }

    @Test
    fun set_reportsFailure() = runTest(dispatcher) {
        val vm = viewModel { _, _, _ -> false }
        vm.set(track, RingtoneTarget.Alarm, null)
        advanceUntilIdle()
        assertEquals(listOf(RingtoneResult.Failed), results)
    }

    @Test
    fun cancel_isAlwaysReported_neverSilent() = runTest(dispatcher) {
        val vm = viewModel { _, _, _ -> CompletableDeferred<Boolean>().await() }
        vm.set(track, RingtoneTarget.Notification, excerpt)
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertFalse(vm.inProgress.value)
        assertEquals(listOf(RingtoneResult.Cancelled), results)
    }

    @Test
    fun set_whileAnotherIsInProgress_isIgnored() = runTest(dispatcher) {
        val finish = CompletableDeferred<Boolean>()
        var calls = 0
        val vm = viewModel { _, _, _ -> calls++; finish.await() }
        vm.set(track, RingtoneTarget.Call, excerpt)
        advanceUntilIdle()
        vm.set(track, RingtoneTarget.Alarm, excerpt)
        finish.complete(true)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf(RingtoneResult.Set(track, RingtoneTarget.Call)), results)
    }
}
