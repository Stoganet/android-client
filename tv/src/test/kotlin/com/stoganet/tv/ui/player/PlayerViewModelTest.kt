package com.stoganet.tv.ui.player

import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.stoganet.core.api.model.CastMember
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.playback.PlaybackRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses [StandardTestDispatcher], not Unconfined: PlayerViewModel's periodic-tick loop relies on
 * repeated `delay()`, and Unconfined's eager/reentrant execution model is documented as unreliable
 * for that shape of code (each pump must be explicit via `advanceTimeBy`/`runCurrent`).
 *
 * `advanceUntilIdle()` is only used where no tick loop is running — the init block always leaves
 * one indefinitely-suspended (non-scheduled) `listenTo` coroutine, and once `startTick()` fires,
 * the tick coroutine schedules a delay forever, which `advanceUntilIdle()` would drain forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<DetailRepository>()
    private val playbackRepository = mockk<PlaybackRepository>(relaxed = true)
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val mediaSession = mockk<MediaSession>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { player.applicationLooper } returns android.os.Looper.getMainLooper()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeDetail(play: PlayInfo? = fakePlay()) = LibraryDetail(
        id = "tmdb:movie:603",
        title = "The Matrix",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        overview = "A computer hacker learns the truth.",
        state = if (play != null) MediaState.PLAYABLE else MediaState.DOWNLOADING,
        genres = listOf("Action"),
        runtime = 136,
        cast = listOf(CastMember(name = "Keanu Reeves", role = "Actor")),
        seasons = emptyList(),
        play = play,
    )

    private fun fakePlay() = PlayInfo(streamUrl = "https://api.stoganet.com/stream/abc123")

    private fun newVm(streamUrl: String? = "https://api.stoganet.com/stream/abc123") = PlayerViewModel(
        id = "id1",
        repository = repository,
        playbackRepository = playbackRepository,
        player = player,
        mediaSession = mediaSession,
        streamUrl = streamUrl,
    )

    private fun events(vararg events: Int) = Player.Events(FlagSet.Builder().addAll(*events).build())

    @Test
    fun `transitions to Ready when play info available`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = PlayerViewModel(
            id = "id1",
            repository = repository,
            playbackRepository = playbackRepository,
            player = player,
            mediaSession = mediaSession,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Ready)
        verify { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `transitions to Error on fetch failure`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = PlayerViewModel(
            id = "id1",
            repository = repository,
            playbackRepository = playbackRepository,
            player = player,
            mediaSession = mediaSession,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `transitions to Error when play is null`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(play = null))
        val vm = PlayerViewModel(
            id = "id1",
            repository = repository,
            playbackRepository = playbackRepository,
            player = player,
            mediaSession = mediaSession,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `transitions to Ready directly when streamUrl provided without fetching`() = runTest(testDispatcher) {
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Ready)
        verify { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify { player.prepare() }
        coVerify(exactly = 0) { repository.getDetail(any()) }
    }

    @Test
    fun `transitions to Error on player error event`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `playback ended reports progress with played true`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.currentPosition } returns 3_000L
        every { player.playbackState } returns Player.STATE_ENDED
        newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYBACK_STATE_CHANGED))
        testDispatcher.scheduler.runCurrent()

        coVerify { playbackRepository.reportProgress("abc123", 3_000L, true) }
    }

    @Test
    fun `onIntent Exit stops player and reports final progress`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 4_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.Exit)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { player.stop() }
        coVerify { playbackRepository.reportProgress("abc123", 4_000L, false) }
    }

    @Test
    fun `onIntent Exit does not double-report when stop fires isPlaying changed`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.currentPosition } returns 4_000L
        every { player.isPlaying } returns false
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.Exit)
        testDispatcher.scheduler.advanceUntilIdle()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_IS_PLAYING_CHANGED))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { playbackRepository.reportProgress(any(), any(), any()) }
    }
}
