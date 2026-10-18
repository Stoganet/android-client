package com.stoganet.tv.ui.detail

import com.stoganet.core.api.model.CastMember
import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.api.model.Season
import com.stoganet.core.api.model.WatchProgress
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.search.SearchRepository
import com.stoganet.core.util.UserMessageCenter
import com.stoganet.tv.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val repository = mockk<DetailRepository>()
    private val searchRepository = mockk<SearchRepository>()
    private val userMessageCenter = mockk<UserMessageCenter>(relaxed = true)
    private val progressSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val dispatcher = UnconfinedTestDispatcher()

    private fun newVm(id: String = "id1") = DetailViewModel(
        id = id,
        repository = repository,
        searchRepository = searchRepository,
        userMessageCenter = userMessageCenter,
    )

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.detail(any()) } answers { refreshedAfterPlayback { repository.getDetail(firstArg()) } }
        every { repository.episodes(any(), any()) } answers {
            refreshedAfterPlayback { repository.getEpisodes(firstArg(), secondArg()) }
        }
    }

    // Same behaviour as the real repository flows, driven by the stubbed one-off fetches
    private fun <T> refreshedAfterPlayback(fetch: suspend () -> T): Flow<T> =
        progressSaved.onStart { emit(Unit) }.mapLatest { fetch() }

    @AfterEach fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeDetail(
        play: PlayInfo? = PlayInfo(streamUrl = "https://api.stoganet.com/stream/jf-uuid", subtitleTracks = emptyList()),
    ) = LibraryDetail(
        id = "tmdb:movie:603",
        title = "Test Movie",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        backdrop = "https://img/backdrop",
        thumbHasLogo = false,
        overview = "A computer hacker learns the truth.",
        state = if (play != null) MediaState.PLAYABLE else MediaState.DOWNLOADING,
        genres = listOf("Action", "Sci-Fi"),
        runtime = 136,
        cast = listOf(CastMember(name = "Test Actor", role = "Actor")),
        seasons = emptyList(),
        play = play,
    )

    private fun pilot(progress: WatchProgress? = null) = Episode(
        id = "ep1",
        number = 1,
        seasonNumber = 1,
        title = "Pilot",
        state = MediaState.PLAYABLE,
        progress = progress,
    )

    // Detail's UI collects state only while the screen is visible
    private fun TestScope.show(vm: DetailViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    private fun TestScope.openDetail(id: String = "id1"): DetailViewModel = newVm(id).also { show(it) }

    @Test
    fun `loads Content on success`() = runTest(dispatcher) {
        coEvery { repository.getDetail("id1") } returns Result.success(fakeDetail())
        val vm = openDetail()

        val state = vm.state.value
        assertInstanceOf(DetailUiState.Content::class.java, state)
        state as DetailUiState.Content
        assertEquals("Test Movie", state.title)
        assertEquals(1999, state.year)
        assertEquals("https://img/backdrop", state.backdropUrl)
        assertEquals(listOf("Action", "Sci-Fi"), state.genres.toList())
        assertEquals("2h 16m", state.runtime)
        assertEquals(1, state.cast.size)
        assertEquals("Test Actor", state.cast[0].name)
        assertTrue(state.isPlayable)
    }

    @Test
    fun `movie with unfinished progress exposes resume position`() = runTest(dispatcher) {
        coEvery { repository.getDetail("id1") } returns
            Result.success(fakeDetail().copy(progress = WatchProgress(positionMs = 90_000L, played = false)))

        val state = openDetail().state.value as DetailUiState.Content

        assertEquals(90_000L, state.positionMs)
    }

    @Test
    fun `movie never started has no resume position`() = runTest(dispatcher) {
        coEvery { repository.getDetail("id1") } returns Result.success(fakeDetail())

        val state = openDetail().state.value as DetailUiState.Content

        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `finished movie has no resume position`() = runTest(dispatcher) {
        coEvery { repository.getDetail("id1") } returns
            Result.success(fakeDetail().copy(progress = WatchProgress(positionMs = 7_000_000L, played = true)))

        val state = openDetail().state.value as DetailUiState.Content

        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `shows Error on failure`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = openDetail()

        assertInstanceOf(DetailUiState.Error::class.java, vm.state.value)
    }

    @Test
    fun `Retry reloads from scratch`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = openDetail()

        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        vm.onIntent(DetailIntent.Retry)

        assertInstanceOf(DetailUiState.Content::class.java, vm.state.value)
        coVerify(exactly = 2) { repository.getDetail("id1") }
    }

    @Test
    fun `Retry no-ops when already Loading`() = runTest(dispatcher) {
        val deferred = CompletableDeferred<Result<LibraryDetail>>()
        coEvery { repository.getDetail(any()) } coAnswers { deferred.await() }
        val vm = openDetail()
        assertTrue(vm.state.value is DetailUiState.Loading)
        vm.onIntent(DetailIntent.Retry)
        deferred.complete(Result.success(fakeDetail()))
        coVerify(exactly = 1) { repository.getDetail("id1") }
    }

    @Test
    fun `isPlayable is false when play is null`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(play = null))
        val vm = openDetail()

        val state = vm.state.value as DetailUiState.Content
        assertFalse(state.isPlayable)
    }

    @Test
    fun `isPlayable is false when state is not PLAYABLE even with play info`() = runTest(dispatcher) {
        val detail = fakeDetail().copy(state = MediaState.DOWNLOADING)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val vm = openDetail()

        val state = vm.state.value as DetailUiState.Content
        assertFalse(state.isPlayable)
    }

    @Test
    fun `formatRuntime formats hours and minutes`() {
        assertEquals("2h 16m", DetailViewModel.formatRuntime(136))
    }

    @Test
    fun `formatRuntime formats minutes only`() {
        assertEquals("45m", DetailViewModel.formatRuntime(45))
    }

    @Test
    fun `formatRuntime formats whole hours`() {
        assertEquals("2h", DetailViewModel.formatRuntime(120))
    }

    @Test
    fun `formatRuntime returns empty string for zero`() {
        assertEquals("", DetailViewModel.formatRuntime(0))
    }

    @Test
    fun `selectSeason loads episodes on success`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        val detail = fakeDetail().copy(type = MediaType.TV, seasons = listOf(season))
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val episode = Episode(id = "ep1", number = 1, seasonNumber = 1, title = "Pilot", state = MediaState.PLAYABLE)
        coEvery { repository.getEpisodes(any(), 1) } returns Result.success(listOf(episode))

        val vm = openDetail()
        vm.onIntent(DetailIntent.SelectSeason(1))

        val state = vm.state.value as DetailUiState.Content
        assertEquals(1, state.selectedSeason)
        assertEquals(1, state.episodes.size)
        assertEquals("Pilot", state.episodes[0].title)
    }

    @Test
    fun `episodes are playable only with play info`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 2, poster = "")
        val detail = fakeDetail().copy(type = MediaType.TV, seasons = listOf(season))
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val play = PlayInfo(streamUrl = "https://api.stoganet.com/stream/ep1", subtitleTracks = emptyList())
        coEvery { repository.getEpisodes(any(), 1) } returns Result.success(
            listOf(
                Episode(
                    id = "ep1",
                    number = 1,
                    seasonNumber = 1,
                    title = "Pilot",
                    state = MediaState.PLAYABLE,
                    play = play,
                ),
                Episode(id = "ep2", number = 2, seasonNumber = 1, title = "Second", state = MediaState.DOWNLOADING),
            ),
        )

        val vm = openDetail()
        vm.onIntent(DetailIntent.SelectSeason(1))

        val episodes = (vm.state.value as DetailUiState.Content).episodes
        assertEquals(listOf(true, false), episodes.map { it.isPlayable })
    }

    @Test
    fun `progress saved while shown refreshes detail`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        openDetail()

        progressSaved.emit(Unit)

        coVerify(exactly = 2) { repository.getDetail("id1") }
    }

    @Test
    fun `progress saved just after leaving still refreshes detail`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = newVm()
        show(vm).cancel()

        testScheduler.advanceTimeBy(1_000)
        progressSaved.emit(Unit)

        coVerify(exactly = 2) { repository.getDetail("id1") }
    }

    @Test
    fun `coming back after a long time away fetches again`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = newVm()
        show(vm).cancel()
        testScheduler.advanceTimeBy(6_000)

        show(vm)

        coVerify(exactly = 2) { repository.getDetail("id1") }
        assertInstanceOf(DetailUiState.Content::class.java, vm.state.value)
    }

    @Test
    fun `coming back quickly does not fetch again`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = newVm()
        show(vm).cancel()
        testScheduler.advanceTimeBy(1_000)

        show(vm)

        coVerify(exactly = 1) { repository.getDetail("id1") }
    }

    @Test
    fun `progress saved before the screen opened does not refresh`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        progressSaved.tryEmit(Unit)

        openDetail()

        coVerify(exactly = 1) { repository.getDetail("id1") }
    }

    @Test
    fun `refresh shows the new resume point without a loading state`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returnsMany listOf(
            Result.success(fakeDetail()),
            Result.success(fakeDetail().copy(progress = WatchProgress(positionMs = 60_000L, played = false))),
        )
        val vm = openDetail()
        val states = mutableListOf<DetailUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(states) }

        progressSaved.emit(Unit)

        assertEquals(60_000L, (vm.state.value as DetailUiState.Content).positionMs)
        assertFalse(states.any { it is DetailUiState.Loading })
    }

    @Test
    fun `refresh keeps the selected season and reloads its episodes`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        val watched = WatchProgress(positionMs = 60_000L, played = false)
        coEvery { repository.getEpisodes(any(), 1) } returnsMany listOf(
            Result.success(
                listOf(pilot()),
            ),
            Result.success(
                listOf(pilot(progress = watched)),
            ),
        )
        val vm = openDetail()
        vm.onIntent(DetailIntent.SelectSeason(1))

        progressSaved.emit(Unit)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(1, state.selectedSeason)
        assertEquals(60_000L, state.episodes.single().positionMs)
    }

    @Test
    fun `failed refresh keeps the current content`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returnsMany listOf(
            Result.success(fakeDetail()),
            Result.failure(RuntimeException("offline")),
        )
        val vm = openDetail()

        progressSaved.emit(Unit)

        assertEquals("Test Movie", (vm.state.value as DetailUiState.Content).title)
    }

    @Test
    fun `a watched episode plays from the beginning`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        coEvery { repository.getEpisodes(any(), 1) } returns
            Result.success(listOf(pilot(progress = WatchProgress(positionMs = 2_400_000L, played = true))))

        val vm = openDetail()
        vm.onIntent(DetailIntent.SelectSeason(1))

        val episode = (vm.state.value as DetailUiState.Content).episodes.single()
        assertEquals(0L, episode.positionMs)
        assertTrue(episode.played)
    }

    @Test
    fun `selectSeason reverts selectedSeason on fetch failure`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        val detail = fakeDetail().copy(type = MediaType.TV, seasons = listOf(season))
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        coEvery { repository.getEpisodes(any(), 1) } returns Result.failure(RuntimeException("network error"))

        val vm = openDetail()
        vm.onIntent(DetailIntent.SelectSeason(1))

        val state = vm.state.value as DetailUiState.Content
        assertNull(state.selectedSeason)
        assertTrue(state.episodes.isEmpty())
    }

    @Test
    fun `mediaState is surfaced as PLAYABLE`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = openDetail()

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.PLAYABLE, state.mediaState)
    }

    @Test
    fun `mediaState is surfaced as REQUESTABLE`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val vm = openDetail()

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.REQUESTABLE, state.mediaState)
    }

    @Test
    fun `mediaState is surfaced as DOWNLOADING`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(play = null))
        val vm = openDetail()

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.DOWNLOADING, state.mediaState)
    }

    @Test
    fun `RequestMovie success flips mediaState to DOWNLOADING`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        coEvery { searchRepository.requestMovie("id1") } returns Result.success(Unit)
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.DOWNLOADING, state.mediaState)
    }

    @Test
    fun `RequestMovie success does not show a failure message`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        coEvery { searchRepository.requestMovie("id1") } returns Result.success(Unit)
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)

        verify(exactly = 0) { userMessageCenter.show(any()) }
    }

    @Test
    fun `RequestMovie failure reverts mediaState back to REQUESTABLE`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        coEvery { searchRepository.requestMovie("id1") } returns Result.failure(RuntimeException("fail"))
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.REQUESTABLE, state.mediaState)
    }

    @Test
    fun `RequestMovie failure shows a failure message`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        coEvery { searchRepository.requestMovie("id1") } returns Result.failure(RuntimeException("fail"))
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)

        verify(exactly = 1) { userMessageCenter.show(R.string.detail_request_failed_message) }
    }

    @Test
    fun `RequestMovie flips mediaState to DOWNLOADING before the network call resolves`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val deferred = CompletableDeferred<Result<Unit>>()
        coEvery { searchRepository.requestMovie("id1") } coAnswers { deferred.await() }
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(MediaState.DOWNLOADING, state.mediaState)
        deferred.complete(Result.success(Unit))
    }

    @Test
    fun `second RequestMovie tap while already DOWNLOADING does not fire another request`() = runTest(dispatcher) {
        val detail = fakeDetail(play = null).copy(state = MediaState.REQUESTABLE)
        coEvery { repository.getDetail(any()) } returns Result.success(detail)
        val deferred = CompletableDeferred<Result<Unit>>()
        coEvery { searchRepository.requestMovie("id1") } coAnswers { deferred.await() }
        val vm = openDetail()

        vm.onIntent(DetailIntent.RequestMovie)
        vm.onIntent(DetailIntent.RequestMovie)
        deferred.complete(Result.success(Unit))

        coVerify(exactly = 1) { searchRepository.requestMovie("id1") }
    }

    @Test
    fun `a season that failed can be picked again`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        coEvery { repository.getEpisodes(any(), 1) } returnsMany listOf(
            Result.failure(RuntimeException("network error")),
            Result.success(listOf(pilot())),
        )
        val vm = openDetail()

        vm.onIntent(DetailIntent.SelectSeason(1))
        vm.onIntent(DetailIntent.SelectSeason(1))

        val state = vm.state.value as DetailUiState.Content
        assertEquals(1, state.selectedSeason)
        assertEquals("Pilot", state.episodes.single().title)
    }

    @Test
    fun `picking the season already shown does not fetch again`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        coEvery { repository.getEpisodes(any(), 1) } returns Result.success(listOf(pilot()))
        val vm = openDetail()

        vm.onIntent(DetailIntent.SelectSeason(1))
        vm.onIntent(DetailIntent.SelectSeason(1))

        coVerify(exactly = 1) { repository.getEpisodes("id1", 1) }
    }

    @Test
    fun `the picked season is kept after coming back from a long time away`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        coEvery { repository.getEpisodes(any(), 1) } returns Result.success(listOf(pilot()))
        val vm = newVm()
        val screen = show(vm)
        vm.onIntent(DetailIntent.SelectSeason(1))
        screen.cancel()
        testScheduler.advanceTimeBy(6_000)

        show(vm)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(1, state.selectedSeason)
        assertEquals("Pilot", state.episodes.single().title)
    }

    @Test
    fun `coming back after a long time keeps the content while fetching`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = newVm()
        show(vm).cancel()
        testScheduler.advanceTimeBy(6_000)

        val states = mutableListOf<DetailUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(states) }

        assertFalse(states.any { it is DetailUiState.Loading })
    }

    @Test
    fun `picking another season while one is loading shows the newer one`() = runTest(dispatcher) {
        val seasons = listOf(
            Season(number = 1, name = "Season 1", episodeCount = 1, poster = ""),
            Season(number = 2, name = "Season 2", episodeCount = 1, poster = ""),
        )
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = seasons))
        val firstSeason = CompletableDeferred<Result<List<Episode>>>()
        coEvery { repository.getEpisodes(any(), 1) } coAnswers { firstSeason.await() }
        coEvery { repository.getEpisodes(any(), 2) } returns Result.success(listOf(pilot().copy(seasonNumber = 2)))
        val vm = openDetail()

        vm.onIntent(DetailIntent.SelectSeason(1))
        vm.onIntent(DetailIntent.SelectSeason(2))
        firstSeason.complete(Result.success(listOf(pilot())))

        assertEquals(2, (vm.state.value as DetailUiState.Content).selectedSeason)
    }

    @Test
    fun `coming back after a long time keeps the episodes while fetching`() = runTest(dispatcher) {
        val season = Season(number = 1, name = "Season 1", episodeCount = 1, poster = "")
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeDetail().copy(type = MediaType.TV, seasons = listOf(season)))
        coEvery { repository.getEpisodes(any(), 1) } returns Result.success(listOf(pilot()))
        val vm = newVm()
        val screen = show(vm)
        vm.onIntent(DetailIntent.SelectSeason(1))
        screen.cancel()
        testScheduler.advanceTimeBy(6_000)
        val refetch = CompletableDeferred<Result<List<Episode>>>()
        coEvery { repository.getEpisodes(any(), 1) } coAnswers { refetch.await() }

        show(vm)

        val state = vm.state.value as DetailUiState.Content
        assertEquals(1, state.selectedSeason)
        assertEquals("Pilot", state.episodes.single().title)
        refetch.complete(Result.success(listOf(pilot())))
    }
}
