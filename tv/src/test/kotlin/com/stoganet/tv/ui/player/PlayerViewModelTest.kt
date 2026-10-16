package com.stoganet.tv.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.stoganet.core.api.model.CastMember
import com.stoganet.core.api.model.EpisodeInfo
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.NextEpisode
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.api.model.SeriesRef
import com.stoganet.core.api.model.SubtitleTrack
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.playback.PlaybackRepository
import com.stoganet.core.data.player.SubtitlePreferenceStore
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

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
    private val subtitlePreferenceStore = mockk<SubtitlePreferenceStore>()
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val mediaSession = mockk<MediaSession>(relaxed = true)
    private val refreshTokens = mockk<suspend () -> Boolean>()
    private val trackParamsSlot = slot<TrackSelectionParameters>()

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { player.applicationLooper } returns android.os.Looper.getMainLooper()
        every { player.playerError } returns null
        every { player.trackSelectionParameters } returns TrackSelectionParameters.DEFAULT
        every { player.trackSelectionParameters = capture(trackParamsSlot) } just Runs
        every { player.currentTracks } returns Tracks.EMPTY
        coEvery { subtitlePreferenceStore.current() } returns null
        coEvery { subtitlePreferenceStore.savePreferredLanguage(any()) } just Runs
        // loadAndPrepare() always fetches detail; default to a successful fetch with no
        // subtitle tracks so streamUrl-only tests don't need to stub it explicitly.
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeDetail(play: PlayInfo? = fakePlay(), episode: EpisodeInfo? = null) = LibraryDetail(
        id = "tmdb:movie:603",
        title = if (episode != null) "Test Episode" else "Test Movie",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        thumbHasLogo = false,
        overview = "A test movie overview.",
        state = if (play != null) MediaState.PLAYABLE else MediaState.DOWNLOADING,
        genres = listOf("Action"),
        runtime = 136,
        cast = listOf(CastMember(name = "Test Actor", role = "Actor")),
        seasons = emptyList(),
        play = play,
        episode = episode,
    )

    private fun fakeEpisodeInfo() = EpisodeInfo(
        series = SeriesRef(id = "jf:show1", title = "Test Show"),
        seasonNumber = 1,
        number = 3,
    )

    private fun fakePlay(subtitleTracks: List<SubtitleTrack> = emptyList()) = PlayInfo(
        streamUrl = "https://api.stoganet.com/stream/abc123",
        subtitleTracks = subtitleTracks,
    )

    private fun fakeSubtitleTrack(index: Int, language: String, title: String = language, isDefault: Boolean = false) =
        SubtitleTrack(
            index = index,
            language = language,
            title = title,
            codec = "subrip",
            isDefault = isDefault,
            isForced = false,
            isExternal = false,
        )

    // Mirrors what ExoPlayer reports once a media source with sideloaded subtitles is prepared:
    // one text TrackGroup per SubtitleConfiguration. MergingMediaSource prefixes each merged
    // Format.id with "childIndex:", so the id we set via .setId() only survives as a suffix so
    // this must be reproduced here or tests won't catch a regression to exact-id matching.
    private fun fakeTextTracks(vararg indices: Int): Tracks {
        val groups = indices.mapIndexed { childIndex, idx ->
            val format = Format.Builder().setId("$childIndex:$idx").setSampleMimeType(MimeTypes.TEXT_VTT).build()
            Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        }
        return Tracks(groups)
    }

    private fun newVm(streamUrl: String? = "https://api.stoganet.com/stream/abc123") = PlayerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to "id1", "streamUrl" to streamUrl)),
        repository = repository,
        playbackRepository = playbackRepository,
        subtitlePreferenceStore = subtitlePreferenceStore,
        player = player,
        mediaSession = mediaSession,
        refreshTokens = refreshTokens,
    )

    private fun newVmFromDetail(positionMs: Long? = null) = PlayerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to "id1", "positionMs" to positionMs)),
        repository = repository,
        playbackRepository = playbackRepository,
        subtitlePreferenceStore = subtitlePreferenceStore,
        player = player,
        mediaSession = mediaSession,
        refreshTokens = refreshTokens,
    )

    private fun events(vararg events: Int) = Player.Events(FlagSet.Builder().addAll(*events).build())

    private fun httpErrorException(responseCode: Int): ExoPlaybackException {
        val dataSpec = DataSpec(Uri.parse("https://api.stoganet.com/stream/abc123"))
        val cause = HttpDataSource.InvalidResponseCodeException(
            responseCode,
            null,
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0),
        )
        return ExoPlaybackException.createForSource(cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
    }

    private fun unauthorizedException(): ExoPlaybackException = httpErrorException(401)

    @Test
    fun `starts at the position from the route`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        newVmFromDetail(positionMs = 42_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { player.setMediaItem(any<MediaItem>(), 42_000L) }
    }

    @Test
    fun `transitions to Ready when play info available`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail())
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Ready)
        verify { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `transitions to Error on fetch failure`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `transitions to Error when play is null`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(play = null))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `transitions to Ready with given streamUrl even when detail fetch fails`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("fail"))
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Ready)
        verify { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify { player.prepare() }
        coVerify { repository.getDetail(any()) }
    }

    @Test
    fun `streamUrl path has no subtitle tracks`() = runTest(testDispatcher) {
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertTrue(ready.subtitleTracks.isEmpty())
        assertNull(ready.selectedSubtitleIndex)
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
    fun `retries playback with refreshed token on 401 player error`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.playerError } returns unauthorizedException()
        every { player.currentPosition } returns 5_000L
        coEvery { refreshTokens() } returns true
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { refreshTokens() }
        verify { player.setMediaItem(any<MediaItem>(), 0L) }
        verify { player.setMediaItem(any<MediaItem>(), 5_000L) }
        assertTrue(vm.state.value is PlayerUiState.Ready)
    }

    @Test
    fun `transitions to Error when refresh fails after 401`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.playerError } returns unauthorizedException()
        coEvery { refreshTokens() } returns false
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `does not retry a second 401 after one retry already happened`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.playerError } returns unauthorizedException()
        coEvery { refreshTokens() } returns true
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { refreshTokens() }
        assertTrue(vm.state.value is PlayerUiState.Error)
    }

    @Test
    fun `non-401 http player error does not trigger refresh`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.playerError } returns httpErrorException(503)
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { refreshTokens() }
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
    fun `movie detail shows the movie title without an episode line`() = runTest(testDispatcher) {
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals("Test Movie", ready.title)
        assertNull(ready.episodeLine)
    }

    @Test
    fun `episode detail shows the episode title and episode line`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(episode = fakeEpisodeInfo()))
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals("Test Episode", ready.title)
        assertEquals(EpisodeLineUi(seriesTitle = "Test Show", seasonNumber = 1, number = 3), ready.episodeLine)
    }

    @Test
    fun `episode with a next episode exposes it to the controls`() = runTest(testDispatcher) {
        val episode = fakeEpisodeInfo().copy(
            next = NextEpisode(id = "jf:ep4", seasonNumber = 1, number = 4, title = "Next Title"),
        )
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(episode = episode))
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NextEpisodeUi(id = "jf:ep4", positionMs = 0L), (vm.state.value as PlayerUiState.Ready).nextEpisode)
    }

    @Test
    fun `failed detail fetch plays without a title`() = runTest(testDispatcher) {
        coEvery { repository.getDetail(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertNull(ready.title)
        assertNull(ready.episodeLine)
    }

    @Test
    fun `401 retry keeps the title`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(episode = fakeEpisodeInfo()))
        coEvery { refreshTokens() } returns true
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        every { player.playerError } returns unauthorizedException()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals("Test Episode", ready.title)
        assertEquals(EpisodeLineUi(seriesTitle = "Test Show", seasonNumber = 1, number = 3), ready.episodeLine)
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
    fun `onIntent Exit while loading keeps the saved position`() = runTest(testDispatcher) {
        val vm = newVm()

        vm.onIntent(PlayerIntent.Exit)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { playbackRepository.reportProgress(any(), any(), any()) }
    }

    @Test
    fun `detail arriving after exit does not start playback`() = runTest(testDispatcher) {
        val vm = newVm()

        vm.onIntent(PlayerIntent.Exit)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify(exactly = 0) { player.play() }
        assertTrue(vm.state.value is PlayerUiState.Loading)
    }

    @Test
    fun `onIntent Exit releases the media session`() = runTest(testDispatcher) {
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.Exit)

        verify { mediaSession.release() }
    }

    @Test
    fun `onIntent Exit right after a seek reports the seek target and drops the pending seek`() =
        runTest(testDispatcher) {
            every { player.currentPosition } returns 5_000L
            every { player.duration } returns 60_000L
            val vm = newVm()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.onIntent(PlayerIntent.SeekForward())
            vm.onIntent(PlayerIntent.SeekForward())
            vm.onIntent(PlayerIntent.Exit)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { playbackRepository.reportProgress("abc123", 25_000L, false) }
            verify(exactly = 0) { player.seekTo(any()) }
        }

    @Test
    fun `401 retry does not restart playback after exit`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        coEvery { refreshTokens() } coAnswers {
            delay(1_000L.milliseconds)
            true
        }
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        every { player.playerError } returns unauthorizedException()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.runCurrent()
        vm.onIntent(PlayerIntent.Exit)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { player.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `401 retry resumes at a pending seek target`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 60_000L
        coEvery { refreshTokens() } returns true
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward())
        every { player.playerError } returns unauthorizedException()
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYER_ERROR))
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { player.seekTo(any()) }
        verify { player.setMediaItem(any<MediaItem>(), 15_000L) }
        assertNull((vm.state.value as PlayerUiState.Ready).seekPreviewMs)
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

    @Test
    fun `maps subtitle tracks from PlayInfo into Ready state`() = runTest(testDispatcher) {
        val tracks = listOf(
            fakeSubtitleTrack(index = 2, language = "eng", title = "English", isDefault = true),
            fakeSubtitleTrack(index = 3, language = "fin", title = "Finnish"),
        )
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals(2, ready.subtitleTracks.size)
        assertEquals("English", ready.subtitleTracks[0].title)
        assertEquals("fin", ready.subtitleTracks[1].language)
    }

    @Test
    fun `MediaItem gets one SubtitleConfiguration per track pointing at the vtt endpoint`() = runTest(testDispatcher) {
        val tracks = listOf(fakeSubtitleTrack(index = 2, language = "eng", title = "English"))
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val mediaItemSlot = slot<MediaItem>()
        every { player.setMediaItem(capture(mediaItemSlot), any<Long>()) } just Runs
        newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        val configs = mediaItemSlot.captured.localConfiguration?.subtitleConfigurations.orEmpty()
        assertEquals(1, configs.size)
        assertEquals("https://api.stoganet.com/stream/abc123/subtitles/2", configs[0].uri.toString())
    }

    @Test
    fun `auto-selects is_default track when no saved language preference`() = runTest(testDispatcher) {
        val tracks = listOf(
            fakeSubtitleTrack(index = 2, language = "eng", isDefault = false),
            fakeSubtitleTrack(index = 3, language = "fin", isDefault = true),
        )
        every { player.currentTracks } returns fakeTextTracks(2, 3)
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals(3, ready.selectedSubtitleIndex)
        assertFalse(trackParamsSlot.captured.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun `saved language preference wins over is_default track`() = runTest(testDispatcher) {
        coEvery { subtitlePreferenceStore.current() } returns "eng"
        val tracks = listOf(
            fakeSubtitleTrack(index = 2, language = "eng", isDefault = false),
            fakeSubtitleTrack(index = 3, language = "fin", isDefault = true),
        )
        every { player.currentTracks } returns fakeTextTracks(2, 3)
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals(2, ready.selectedSubtitleIndex)
    }

    @Test
    fun `no default and no preference leaves subtitles off`() = runTest(testDispatcher) {
        val tracks = listOf(fakeSubtitleTrack(index = 2, language = "eng", isDefault = false))
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertNull(ready.selectedSubtitleIndex)
        assertTrue(trackParamsSlot.captured.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun `SelectSubtitleTrack updates selection and persists language`() = runTest(testDispatcher) {
        val tracks = listOf(fakeSubtitleTrack(index = 2, language = "eng", title = "English"))
        every { player.currentTracks } returns fakeTextTracks(2)
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SelectSubtitleTrack(2))
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertEquals(2, ready.selectedSubtitleIndex)
        assertFalse(ready.subtitleMenuOpen)
        coVerify { subtitlePreferenceStore.savePreferredLanguage("eng") }
    }

    @Test
    fun `selects the exact track group when two tracks share a language`() = runTest(testDispatcher) {
        val tracks = listOf(
            fakeSubtitleTrack(index = 2, language = "eng", title = "English"),
            fakeSubtitleTrack(index = 5, language = "eng", title = "English (external)"),
        )
        every { player.currentTracks } returns fakeTextTracks(2, 5)
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SelectSubtitleTrack(5))
        testDispatcher.scheduler.advanceUntilIdle()

        val selectedGroup = trackParamsSlot.captured.overrides.values.single().mediaTrackGroup
        assertEquals("1:5", selectedGroup.getFormat(0).id)
    }

    @Test
    fun `SelectSubtitleTrack null turns subtitles off without persisting`() = runTest(testDispatcher) {
        coEvery { subtitlePreferenceStore.current() } returns "eng"
        val tracks = listOf(fakeSubtitleTrack(index = 2, language = "eng", isDefault = true))
        coEvery { repository.getDetail(any()) } returns Result.success(fakeDetail(fakePlay(tracks)))
        val vm = newVmFromDetail()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SelectSubtitleTrack(null))
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as PlayerUiState.Ready
        assertNull(ready.selectedSubtitleIndex)
        assertTrue(trackParamsSlot.captured.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        coVerify(exactly = 0) { subtitlePreferenceStore.savePreferredLanguage(any()) }
    }

    @Test
    fun `OpenSubtitleMenu and CloseSubtitleMenu toggle menu visibility`() = runTest(testDispatcher) {
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.OpenSubtitleMenu)
        assertTrue((vm.state.value as PlayerUiState.Ready).subtitleMenuOpen)

        vm.onIntent(PlayerIntent.CloseSubtitleMenu)
        assertFalse((vm.state.value as PlayerUiState.Ready).subtitleMenuOpen)
    }

    @Test
    fun `TogglePlayPause pauses a playing player`() = runTest(testDispatcher) {
        every { player.isPlaying } returns true
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.TogglePlayPause)

        verify { player.pause() }
    }

    @Test
    fun `TogglePlayPause plays a paused player`() = runTest(testDispatcher) {
        every { player.isPlaying } returns false
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.TogglePlayPause)

        verify(atLeast = 2) { player.play() }
    }

    @Test
    fun `seek presses move the preview without seeking the player`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 60_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward())
        vm.onIntent(PlayerIntent.SeekForward())
        testDispatcher.scheduler.runCurrent()

        assertEquals(25_000L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)
        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun `seek commits once after input pauses`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 60_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward())
        vm.onIntent(PlayerIntent.SeekForward())
        vm.onIntent(PlayerIntent.SeekBackward())
        testDispatcher.scheduler.advanceTimeBy(1_000L)
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 1) { player.seekTo(any()) }
        verify { player.seekTo(15_000L) }
        assertNull((vm.state.value as PlayerUiState.Ready).seekPreviewMs)
    }

    @Test
    fun `seek preview stays between start and 10 seconds before the end`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 20_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekBackward())
        assertEquals(0L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)

        repeat(5) { vm.onIntent(PlayerIntent.SeekForward()) }
        assertEquals(10_000L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)
    }

    @Test
    fun `held seek key uses the faster tier`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 0L
        every { player.duration } returns 10_000_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward(repeatCount = 20))

        assertEquals(30_000L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)
    }

    @Test
    fun `fresh press after a hold starts at the smallest step`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 0L
        every { player.duration } returns 10_000_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward(repeatCount = 60))
        vm.onIntent(PlayerIntent.SeekBackward())

        assertEquals(110_000L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)
    }

    @Test
    fun `duration updates once the player learns it`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.duration } returns C.TIME_UNSET
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0L, (vm.state.value as PlayerUiState.Ready).durationMs)

        every { player.duration } returns 60_000L
        listenerSlot.captured.onEvents(player, events(Player.EVENT_TIMELINE_CHANGED))
        testDispatcher.scheduler.runCurrent()

        assertEquals(60_000L, (vm.state.value as PlayerUiState.Ready).durationMs)
    }

    @Test
    fun `seek step grows through each tier with the key repeat count`() {
        assertEquals(10_000L, seekStepMs(0))
        assertEquals(10_000L, seekStepMs(19))
        assertEquals(30_000L, seekStepMs(20))
        assertEquals(60_000L, seekStepMs(40))
        assertEquals(120_000L, seekStepMs(60))
        assertEquals(120_000L, seekStepMs(500))
    }

    @Test
    fun `forward seek near the end does not move the position back`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 15_000L
        every { player.duration } returns 20_000L
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward())
        testDispatcher.scheduler.advanceTimeBy(1_000L)
        testDispatcher.scheduler.runCurrent()

        assertNull((vm.state.value as PlayerUiState.Ready).seekPreviewMs)
        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun `forward seek is ignored while duration is unknown`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns C.TIME_UNSET
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekForward())
        testDispatcher.scheduler.advanceTimeBy(1_000L)
        testDispatcher.scheduler.runCurrent()

        assertNull((vm.state.value as PlayerUiState.Ready).seekPreviewMs)
        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun `backward seek works while duration is unknown`() = runTest(testDispatcher) {
        every { player.currentPosition } returns 30_000L
        every { player.duration } returns C.TIME_UNSET
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(PlayerIntent.SeekBackward())

        assertEquals(20_000L, (vm.state.value as PlayerUiState.Ready).seekPreviewMs)
    }

    @Test
    fun `buffering event sets isBuffering and ready clears it`() = runTest(testDispatcher) {
        val listenerSlot = slot<Player.Listener>()
        every { player.addListener(capture(listenerSlot)) } just Runs
        val vm = newVm()
        testDispatcher.scheduler.advanceUntilIdle()

        every { player.playbackState } returns Player.STATE_BUFFERING
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYBACK_STATE_CHANGED))
        testDispatcher.scheduler.runCurrent()
        assertTrue((vm.state.value as PlayerUiState.Ready).isBuffering)

        every { player.playbackState } returns Player.STATE_READY
        listenerSlot.captured.onEvents(player, events(Player.EVENT_PLAYBACK_STATE_CHANGED))
        testDispatcher.scheduler.runCurrent()
        assertFalse((vm.state.value as PlayerUiState.Ready).isBuffering)
    }
}
