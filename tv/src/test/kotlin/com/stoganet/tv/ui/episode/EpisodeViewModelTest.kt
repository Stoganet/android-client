package com.stoganet.tv.ui.episode

import com.stoganet.core.api.model.CastMember
import com.stoganet.core.api.model.EpisodeInfo
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.NextEpisode
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.api.model.SeriesRef
import com.stoganet.core.api.model.WatchProgress
import com.stoganet.core.data.detail.DetailRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeViewModelTest {

    private val repository = mockk<DetailRepository>()
    private val progressSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val dispatcher = UnconfinedTestDispatcher()
    private val defaultLocale = Locale.getDefault()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        Locale.setDefault(Locale.US)
        every { repository.detail(any()) } answers { refreshedAfterPlayback { repository.getDetail(firstArg()) } }
    }

    @AfterEach fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    // Same behaviour as the real repository flow, driven by the stubbed one-off fetch
    private fun <T> refreshedAfterPlayback(fetch: suspend () -> T): Flow<T> =
        progressSaved.onStart { emit(Unit) }.mapLatest { fetch() }

    private fun fakeEpisode(
        play: PlayInfo? = PlayInfo(streamUrl = "https://api.stoganet.com/stream/jf-uuid", subtitleTracks = emptyList()),
        hasEpisodeInfo: Boolean = true,
        airDate: String? = "2008-02-10",
        next: NextEpisode? = NextEpisode(id = "ep4", seasonNumber = 1, number = 4, title = "Next Test Episode"),
    ) = LibraryDetail(
        id = "ep3",
        title = "Test Episode",
        year = 2008,
        type = MediaType.TV,
        poster = "https://img/still",
        backdrop = "https://img/backdrop",
        thumbHasLogo = false,
        overview = "An episode overview.",
        state = if (play != null) MediaState.PLAYABLE else MediaState.DOWNLOADING,
        genres = emptyList(),
        runtime = 48,
        cast = listOf(CastMember(name = "Test Actor", role = "Actor")),
        seasons = emptyList(),
        play = play,
        episode = if (hasEpisodeInfo) {
            EpisodeInfo(
                series = SeriesRef(id = "show1", title = "Test Show"),
                seasonNumber = 1,
                number = 3,
                airDate = airDate,
                next = next,
            )
        } else {
            null
        },
    )

    private fun TestScope.openEpisode(id: String = "ep3"): EpisodeViewModel =
        EpisodeViewModel(id = id, repository = repository).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} }
        }

    private fun EpisodeViewModel.content() = state.value as EpisodeUiState.Content

    @Test
    fun `Loading becomes Content on success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<LibraryDetail>>()
        coEvery { repository.getDetail("ep3") } coAnswers { gate.await() }
        val vm = openEpisode()
        assertInstanceOf(EpisodeUiState.Loading::class.java, vm.state.value)

        gate.complete(Result.success(fakeEpisode()))

        val state = vm.content()
        assertEquals("ep3", state.playId)
        assertEquals("Test Episode", state.title)
        assertEquals("48m", state.runtime)
        assertEquals("An episode overview.", state.overview)
        assertEquals("https://img/backdrop", state.backdropUrl)
        assertEquals("https://img/still", state.stillUrl)
        assertEquals("Test Actor", state.cast.single().name)
        assertEquals("Test Show", state.seriesTitle)
        assertEquals(1, state.seasonNumber)
        assertEquals(3, state.episodeNumber)
    }

    @Test
    fun `shows Error when the first load fails`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))

        assertInstanceOf(EpisodeUiState.Error::class.java, openEpisode().state.value)
    }

    @Test
    fun `failed refresh keeps the current content`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returnsMany listOf(
            Result.success(fakeEpisode()),
            Result.failure(RuntimeException("fail")),
        )
        val vm = openEpisode()

        progressSaved.emit(Unit)

        assertEquals("Test Episode", vm.content().title)
    }

    @Test
    fun `Retry reloads from scratch`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = openEpisode()

        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode())
        vm.onIntent(EpisodeIntent.Retry)

        assertInstanceOf(EpisodeUiState.Content::class.java, vm.state.value)
        coVerify(exactly = 2) { repository.getDetail("ep3") }
    }

    @Test
    fun `Retry shows Loading while the reload is in flight`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = openEpisode()
        val gate = CompletableDeferred<Result<LibraryDetail>>()
        coEvery { repository.getDetail(any()) } coAnswers { gate.await() }

        vm.onIntent(EpisodeIntent.Retry)

        assertInstanceOf(EpisodeUiState.Loading::class.java, vm.state.value)
        gate.cancel()
    }

    @Test
    fun `Retry no-ops when already Loading`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<LibraryDetail>>()
        coEvery { repository.getDetail(any()) } coAnswers { gate.await() }
        val vm = openEpisode()
        assertInstanceOf(EpisodeUiState.Loading::class.java, vm.state.value)

        vm.onIntent(EpisodeIntent.Retry)

        gate.complete(Result.success(fakeEpisode()))
        coVerify(exactly = 1) { repository.getDetail("ep3") }
    }

    @Test
    fun `progress saved refreshes the resume position without a loading state`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returnsMany listOf(
            Result.success(fakeEpisode()),
            Result.success(fakeEpisode().copy(progress = WatchProgress(positionMs = 60_000L, played = false))),
        )
        val vm = openEpisode()
        assertEquals(0L, vm.content().positionMs)
        val states = mutableListOf<EpisodeUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(states) }

        progressSaved.emit(Unit)

        assertEquals(60_000L, vm.content().positionMs)
        assertFalse(states.any { it is EpisodeUiState.Loading })
    }

    @Test
    fun `air date is formatted`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode())

        assertEquals("Feb 10, 2008", openEpisode().content().airDate)
    }

    @Test
    fun `without episode info series, numbers and next are null`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode(hasEpisodeInfo = false))

        val state = openEpisode().content()

        assertNull(state.seriesTitle)
        assertNull(state.seasonNumber)
        assertNull(state.episodeNumber)
        assertNull(state.airDate)
        assertNull(state.nextEpisodeId)
    }

    @Test
    fun `unfinished progress exposes the resume position`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode().copy(progress = WatchProgress(positionMs = 90_000L, played = false)))

        val state = openEpisode().content()

        assertEquals(90_000L, state.positionMs)
        assertFalse(state.played)
    }

    @Test
    fun `never started has no resume position`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode())

        val state = openEpisode().content()

        assertEquals(0L, state.positionMs)
        assertFalse(state.played)
    }

    @Test
    fun `progress fraction is the position over the runtime in milliseconds`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode().copy(progress = WatchProgress(positionMs = 1_440_000L, played = false)))

        assertEquals(0.5f, openEpisode().content().progressFraction, 0.001f)
    }

    @Test
    fun `watched episode has no resume position and is marked played`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode().copy(progress = WatchProgress(positionMs = 2_800_000L, played = true)))

        val state = openEpisode().content()

        assertEquals(0L, state.positionMs)
        assertTrue(state.played)
    }

    @Test
    fun `playable with play info and PLAYABLE state`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode())

        assertTrue(openEpisode().content().isPlayable)
    }

    @Test
    fun `not playable when play is null`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode(play = null).copy(state = MediaState.PLAYABLE))

        assertFalse(openEpisode().content().isPlayable)
    }

    @Test
    fun `not playable when state is not PLAYABLE even with play info`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode().copy(state = MediaState.DOWNLOADING))

        assertFalse(openEpisode().content().isPlayable)
    }

    @Test
    fun `next episode id comes from the episode info`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeEpisode())

        assertEquals("ep4", openEpisode().content().nextEpisodeId)
    }

    @Test
    fun `last episode has no next episode id`() = runTest(dispatcher) {
        coEvery { repository.getDetail(any()) } returns
            Result.success(fakeEpisode(next = null))

        assertNull(openEpisode().content().nextEpisodeId)
    }
}
