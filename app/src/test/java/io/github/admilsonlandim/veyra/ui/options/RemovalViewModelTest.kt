package io.github.admilsonlandim.veyra.ui.options

import androidx.lifecycle.SavedStateHandle
import io.github.admilsonlandim.veyra.data.media.RemovalFailure
import io.github.admilsonlandim.veyra.data.media.RemovalMode
import io.github.admilsonlandim.veyra.data.media.RemovalStep
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackFileRemover
import io.github.admilsonlandim.veyra.data.media.toTrackKey
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemovalViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val track = Track(
        id = 7, contentUri = "content://media/external/audio/media/7", title = "Soja", artist = "Black Sherif",
        album = null, albumId = 0, durationMs = 1, displayName = "soja.mp3", relativePath = "Music/",
        sizeBytes = 1, dateModifiedSeconds = 0,
    )

    /** Answers [start] and [onSystemConfirmed] with the queued steps, recording the calls. */
    private class FakeRemover(override val mode: RemovalMode, vararg steps: RemovalStep) : TrackFileRemover {
        val steps = ArrayDeque(steps.toList())
        val calls = mutableListOf<String>()
        override suspend fun start(track: Track): RemovalStep = steps.removeFirst().also { calls += "start" }
        override suspend fun onSystemConfirmed(track: Track): RemovalStep = steps.removeFirst().also { calls += "confirmed" }
    }

    private val cleaned = mutableListOf<Pair<Track, RemovalMode>>()
    private val events = mutableListOf<RemovalEvent>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        remover: FakeRemover,
        ringtones: Set<RingtoneTarget> = emptySet(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) =
        RemovalViewModel(remover, { ringtones }, { t, mode -> cleaned += t to mode }, savedState).also { vm ->
            // Unconfined: each event is recorded as soon as it is sent.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        }

    @Test
    fun request_showsTheConfirmation_withTheRingtonesUsingTheFile_andRemovesNothing() = runTest(dispatcher) {
        val remover = FakeRemover(RemovalMode.Delete)
        val vm = viewModel(remover, setOf(RingtoneTarget.Alarm))
        vm.request(track)
        advanceUntilIdle()
        assertEquals(RemovalConfirmation(track, RemovalMode.Delete, setOf(RingtoneTarget.Alarm)), vm.confirmation.value)
        assertTrue(remover.calls.isEmpty())
    }

    @Test
    fun dismiss_cancelsWithoutAnything() = runTest(dispatcher) {
        val remover = FakeRemover(RemovalMode.Delete, RemovalStep.Removed)
        val vm = viewModel(remover)
        vm.request(track)
        advanceUntilIdle()
        vm.dismiss()
        vm.confirm() // nothing left to confirm
        advanceUntilIdle()
        assertNull(vm.confirmation.value)
        assertTrue(remover.calls.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun confirm_removed_cleansUpThenReports() = runTest(dispatcher) {
        val vm = viewModel(FakeRemover(RemovalMode.Delete, RemovalStep.Removed))
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertNull(vm.confirmation.value)
        assertEquals(listOf(track to RemovalMode.Delete), cleaned)
        assertEquals(listOf<RemovalEvent>(RemovalEvent.Removed(track, RemovalMode.Delete)), events)
    }

    @Test
    fun permissionGranted_triesAgain_andFinishes() = runTest(dispatcher) {
        val remover = FakeRemover(RemovalMode.Trash, RemovalStep.NeedsWritePermission, RemovalStep.Removed)
        val vm = viewModel(remover)
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf<RemovalEvent>(RemovalEvent.AskWritePermission), events)
        vm.onPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(listOf("start", "start"), remover.calls)
        assertEquals(RemovalEvent.Removed(track, RemovalMode.Trash), events.last())
    }

    @Test
    fun systemDialogCancelled_isNotAnError() = runTest(dispatcher) {
        val remover = FakeRemover(RemovalMode.Delete, RemovalStep.NeedsWritePermission)
        val vm = viewModel(remover)
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        vm.onSystemResult(confirmed = false)
        advanceUntilIdle()
        assertEquals(listOf<RemovalEvent>(RemovalEvent.AskWritePermission), events)
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun systemDialogConfirmed_continuesWithTheSameTrack() = runTest(dispatcher) {
        val remover = FakeRemover(RemovalMode.Delete, RemovalStep.NeedsWritePermission, RemovalStep.Removed)
        val vm = viewModel(remover)
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        vm.onSystemResult(confirmed = true)
        advanceUntilIdle()
        assertEquals(listOf("start", "confirmed"), remover.calls)
        assertEquals(listOf(track to RemovalMode.Delete), cleaned)
        // A late second result does nothing.
        vm.onSystemResult(confirmed = true)
        advanceUntilIdle()
        assertEquals(2, remover.calls.size)
    }

    @Test
    fun permissionRefused_reportsIt() = runTest(dispatcher) {
        val vm = viewModel(FakeRemover(RemovalMode.Delete, RemovalStep.NeedsWritePermission))
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        vm.onPermissionResult(granted = false)
        advanceUntilIdle()
        assertEquals(RemovalEvent.Failed(RemovalFailure.NoPermission), events.last())
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun failure_reportsTheReason_withoutCleaningUp() = runTest(dispatcher) {
        val vm = viewModel(FakeRemover(RemovalMode.Delete, RemovalStep.Failed(RemovalFailure.ReadOnly)))
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf<RemovalEvent>(RemovalEvent.Failed(RemovalFailure.ReadOnly)), events)
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun pendingTrack_survivesTheProcessBeingRecreated_whileTheSystemAsks() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val before = viewModel(FakeRemover(RemovalMode.Delete, RemovalStep.NeedsWritePermission), savedState = savedState)
        before.request(track)
        advanceUntilIdle()
        before.confirm()
        advanceUntilIdle()
        assertEquals(listOf<RemovalEvent>(RemovalEvent.AskWritePermission), events)

        // Android killed the app during the dialog: a new ViewModel gets the saved state back.
        val remover = FakeRemover(RemovalMode.Delete, RemovalStep.Removed)
        val after = viewModel(remover, savedState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        after.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(listOf("start"), remover.calls)
        val (restored, mode) = cleaned.single()
        assertEquals(RemovalMode.Delete, mode)
        // Everything removal and clean-up rely on (URI and TrackKey) came back.
        assertEquals(track.toTrackKey(), restored.toTrackKey())
        assertEquals(track.contentUri, restored.contentUri)
        assertEquals(track.title, restored.title)
    }

    @Test
    fun systemNotConfirmingTheRemoval_cleansNothingUp() = runTest(dispatcher) {
        // The remover reports the file still there after the system said OK.
        val remover = FakeRemover(RemovalMode.Trash, RemovalStep.NeedsWritePermission, RemovalStep.Failed(RemovalFailure.Other))
        val vm = viewModel(remover)
        vm.request(track)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        vm.onSystemResult(confirmed = true)
        advanceUntilIdle()
        assertTrue(cleaned.isEmpty())
        assertEquals(RemovalEvent.Failed(RemovalFailure.Other), events.last())
    }
}
