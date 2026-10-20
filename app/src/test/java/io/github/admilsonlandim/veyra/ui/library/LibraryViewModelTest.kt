package io.github.admilsonlandim.veyra.ui.library

import io.github.admilsonlandim.veyra.data.media.LibraryView
import io.github.admilsonlandim.veyra.data.media.Track
import io.github.admilsonlandim.veyra.data.media.TrackRepository
import io.github.admilsonlandim.veyra.data.media.TrackSort
import io.github.admilsonlandim.veyra.data.settings.LibraryPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val repository = FakeTrackRepository()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        // Main must be replaced before the ViewModel exists: stateIn starts in viewModelScope.
        Dispatchers.setMain(dispatcher)
        viewModel = LibraryViewModel(repository, sortDispatcher = dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeTrackRepository : TrackRepository {
        val library = MutableStateFlow<List<Track>>(emptyList())
        var failNext = false
        var subscriptions = 0

        override fun tracks(): Flow<List<Track>> = flow {
            subscriptions++
            if (failNext) {
                failNext = false
                throw SecurityException("permission revoked")
            }
            library.collect { emit(it) }
        }
    }

    /** Keeps uiState subscribed, since it only runs upstream while collected (WhileSubscribed). */
    private fun TestScope.observe() = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

    @Test
    fun startsLoadingUntilPermissionIsChecked() = runTest(dispatcher) {
        observe()
        advanceUntilIdle()
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun notGranted_asksForPermission() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionChecked(granted = false)
        advanceUntilIdle()
        assertEquals(LibraryUiState.PermissionRequired(AudioPermission.NotRequested), viewModel.uiState.value)
    }

    @Test
    fun deniedWithRationale_isDenied_andStaysDeniedOnResume() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionChecked(granted = false)
        viewModel.onPermissionResult(granted = false, shouldShowRationale = true)
        viewModel.onPermissionChecked(granted = false)
        advanceUntilIdle()
        assertEquals(LibraryUiState.PermissionRequired(AudioPermission.Denied), viewModel.uiState.value)
    }

    @Test
    fun deniedWithoutRationale_isPermanentlyDenied() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
        viewModel.onPermissionChecked(granted = false)
        advanceUntilIdle()
        assertEquals(LibraryUiState.PermissionRequired(AudioPermission.PermanentlyDenied), viewModel.uiState.value)
    }

    @Test
    fun grantedInSettingsAfterPermanentDenial_loadsLibrary() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LibraryUiState.Empty(), viewModel.uiState.value)
    }

    @Test
    fun grantedWithNoTracks_isEmpty() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()
        assertEquals(LibraryUiState.Empty(), viewModel.uiState.value)
    }

    @Test
    fun grantedWithTracks_showsThemSortedByTitle_andFollowsLibraryChanges() = runTest(dispatcher) {
        observe()
        repository.library.value = listOf(track(1, "Zeta"), track(2, "alpha"))
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L), (viewModel.uiState.value as LibraryUiState.Content).tracks.map { it.id })

        repository.library.value = listOf(track(1, "Zeta"), track(2, "alpha"), track(3, "Ómega"))
        advanceUntilIdle()
        assertEquals(listOf(2L, 3L, 1L), (viewModel.uiState.value as LibraryUiState.Content).tracks.map { it.id })

        repository.library.value = emptyList()
        advanceUntilIdle()
        assertEquals(LibraryUiState.Empty(), viewModel.uiState.value)
    }

    @Test
    fun repositoryFailure_showsError_andRetryRecovers() = runTest(dispatcher) {
        observe()
        repository.failNext = true
        repository.library.value = listOf(track(1, "Song"))
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(LibraryUiState.Error, viewModel.uiState.value)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value as LibraryUiState.Content).tracks.size)
        assertEquals(2, repository.subscriptions)
    }

    @Test
    fun repeatedResumesWhileGranted_doNotRequery() = runTest(dispatcher) {
        observe()
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        viewModel.onPermissionChecked(granted = true)
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(1, repository.subscriptions)
    }

    private fun track(id: Long, title: String, duration: Long = 1_000, path: String? = null) = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = title, artist = null,
        album = null, albumId = 0, durationMs = duration, displayName = null, relativePath = path,
        sizeBytes = 0, dateModifiedSeconds = 0,
    )

    @Test
    fun preferences_filterAndSortTheVisibleList_butKeepAllTracks() = runTest(dispatcher) {
        val prefs = MutableStateFlow(LibraryPreferences(sort = TrackSort.Duration, excludedFolders = setOf("Recordings/")))
        val vm = LibraryViewModel(repository, preferences = prefs, sortDispatcher = dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        repository.library.value = listOf(
            track(1, "a", duration = 300_000),
            track(2, "b", duration = 100_000),
            track(3, "rec", path = "Recordings/"),
        )
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val content = vm.uiState.value as LibraryUiState.Content
        assertEquals(listOf(2L, 1L), content.tracks.map { it.id })
        assertEquals(3, content.allTracks.size)

        prefs.value = prefs.value.copy(sort = TrackSort.Title, skipShortTracks = true, minDurationSeconds = 200)
        advanceUntilIdle()
        assertEquals(listOf(1L), (vm.uiState.value as LibraryUiState.Content).tracks.map { it.id })
    }

    private val content get() = viewModel.uiState.value as LibraryUiState.Content

    @Test
    fun playAll_isVisibleOrderFromTheStart_shuffleOff() = runTest(dispatcher) {
        observe()
        repository.library.value = listOf(track(1, "Zeta"), track(2, "alpha"))
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val request = playAllRequest(content.tracks)!!
        assertEquals(listOf(2L, 1L), request.tracks.map { it.id })
        assertEquals(0, request.startIndex)
        assertEquals(false, request.shuffle)
    }

    @Test
    fun shuffle_turnsShuffleOn_andStartsAtARandomVisibleTrack() = runTest(dispatcher) {
        observe()
        repository.library.value = listOf(track(1, "a"), track(2, "b"), track(3, "c"))
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val request = shuffleRequest(content.tracks, Random(seed = 7))!!
        assertEquals(listOf(1L, 2L, 3L), request.tracks.map { it.id })
        assertEquals(true, request.shuffle)
        assertEquals(Random(seed = 7).nextInt(3), request.startIndex)
    }

    @Test
    fun playRequests_areNullWithoutTracks() {
        assertEquals(null, playAllRequest(emptyList()))
        assertEquals(null, shuffleRequest(emptyList()))
    }

    private fun untagged(id: Long, fileName: String, path: String = "VidMate/download/") = Track(
        id = id, contentUri = "content://media/external/audio/media/$id", title = fileName.substringBeforeLast('.'),
        artist = null, album = null, albumId = 0, durationMs = 1_000, displayName = fileName, relativePath = path,
        sizeBytes = 0, dateModifiedSeconds = 0,
    )

    @Test
    fun cleanNames_onByDefault_andTheSettingRestoresTheOriginalNames() = runTest(dispatcher) {
        val prefs = MutableStateFlow(LibraryPreferences())
        val vm = LibraryViewModel(repository, preferences = prefs, sortDispatcher = dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        repository.library.value = listOf(untagged(1, "Black_Sherif_-_Soja__Official_Visualizer_(256k).mp3"))
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val cleaned = (vm.uiState.value as LibraryUiState.Content).allTracks.single()
        assertEquals("Soja" to "Black Sherif", cleaned.title to cleaned.artist)
        assertEquals("Black_Sherif_-_Soja__Official_Visualizer_(256k).mp3", cleaned.displayName)

        prefs.value = prefs.value.copy(cleanNames = false)
        advanceUntilIdle()
        val original = (vm.uiState.value as LibraryUiState.Content).allTracks.single()
        assertEquals("Black_Sherif_-_Soja__Official_Visualizer_(256k)" to null, original.title to original.artist)
    }

    @Test
    fun content_groupsTheVisibleTracksByFolderAndArtist() = runTest(dispatcher) {
        val prefs = MutableStateFlow(LibraryPreferences(view = LibraryView.Artists, excludedFolders = setOf("Recordings/")))
        val vm = LibraryViewModel(repository, preferences = prefs, sortDispatcher = dispatcher)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        repository.library.value = listOf(
            untagged(1, "Black_Sherif_-_Soja.mp3"),
            untagged(2, "Khaid_-_Jolie.mp3", path = "Music/"),
            untagged(3, "Black_Sherif_-_Kwaku.mp3", path = "Recordings/"),
        )
        vm.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val state = vm.uiState.value as LibraryUiState.Content
        assertEquals(LibraryView.Artists, state.view)
        assertEquals(listOf("download", "Music"), state.folders.map { it.name })
        assertEquals(listOf("Black Sherif", "Khaid"), state.artists.map { it.name })
        assertEquals(listOf(1L), state.artists.first().tracks.map { it.id })
    }

    @Test
    fun search_waitsForAPauseInTyping_ignoresAccents_andClosesToNull() = runTest(dispatcher) {
        observe()
        backgroundScope.launch(dispatcher) { viewModel.searchResults.collect {} }
        repository.library.value = listOf(track(1, "João e Maria"), track(2, "Other"), track(3, "Joana"))
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()

        viewModel.setSearchQuery("j")
        viewModel.setSearchQuery("joao")
        advanceTimeBy(100)
        assertNull(viewModel.searchResults.value)
        advanceUntilIdle()
        val results = viewModel.searchResults.value!!
        assertEquals("joao", results.query)
        assertEquals(listOf(1L), results.tracks.map { it.id })

        viewModel.setSearchQuery("zzz")
        advanceUntilIdle()
        assertEquals(emptyList<Track>(), viewModel.searchResults.value!!.tracks)

        // Open but empty, or closed: no results, the normal list shows.
        viewModel.setSearchQuery("")
        advanceUntilIdle()
        assertNull(viewModel.searchResults.value)
        viewModel.setSearchQuery("jo")
        advanceUntilIdle()
        viewModel.setSearchQuery(null)
        advanceUntilIdle()
        assertNull(viewModel.searchResults.value)
    }

    @Test
    fun search_filtersFoldersAndArtistsByName() = runTest(dispatcher) {
        observe()
        backgroundScope.launch(dispatcher) { viewModel.searchResults.collect {} }
        repository.library.value = listOf(
            untagged(1, "Black_Sherif_-_Soja.mp3"),
            untagged(2, "Khaid_-_Jolie.mp3", path = "Music/Afro/"),
        )
        viewModel.onPermissionChecked(granted = true)
        viewModel.setSearchQuery("afro")
        advanceUntilIdle()
        val results = viewModel.searchResults.value!!
        assertEquals(listOf("Afro"), results.folders.map { it.name })
        assertEquals(listOf(2L), results.tracks.map { it.id })
        assertEquals(emptyList<String>(), results.artists.map { it.name })

        viewModel.setSearchQuery("SHERIF")
        advanceUntilIdle()
        assertEquals(listOf("Black Sherif"), viewModel.searchResults.value!!.artists.map { it.name })
    }
}
