package com.stoganet.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.listenTo
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.net.BASE_URL
import com.stoganet.core.data.net.performTokenRefresh
import com.stoganet.core.data.playback.PlaybackRepository
import com.stoganet.core.data.player.SubtitlePreferenceStore
import com.stoganet.tv.StoganetApp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: DetailRepository,
    private val playbackRepository: PlaybackRepository,
    private val subtitlePreferenceStore: SubtitlePreferenceStore,
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val refreshTokens: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var item = NowPlaying(
        id = checkNotNull(savedStateHandle[KEY_ID]),
        routeStreamUrl = savedStateHandle[KEY_STREAM_URL],
        startPositionMs = savedStateHandle[KEY_POSITION_MS] ?: 0L,
    )
    private var tickJob: Job? = null
    private var uiTickJob: Job? = null
    private var hideControlsJob: Job? = null
    private var seekCommitJob: Job? = null
    private var isExiting = false

    init {
        viewModelScope.launch {
            player.listenTo(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAYER_ERROR,
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            ) { events ->
                if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                    handlePlayerError()
                }
                if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                    applyDesiredSubtitleTrack()
                }
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                    updatePlaybackPosition()
                }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    if (isPlaying) {
                        startTick()
                        startUiTick()
                    } else {
                        stopTick()
                        stopUiTick()
                        updatePlaybackPosition()
                        if (!isExiting) reportProgress(played = false)
                    }
                }
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    updateBuffering()
                    if (playbackState == Player.STATE_ENDED) reportProgress(played = true)
                }
            }
        }
        loadAndPrepare()
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Exit -> exit()
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            is PlayerIntent.SeekBackward -> seekBy(direction = -1, repeatCount = intent.repeatCount)
            is PlayerIntent.SeekForward -> seekBy(direction = 1, repeatCount = intent.repeatCount)
            PlayerIntent.ShowControls -> showControls()
            PlayerIntent.OpenSubtitleMenu -> openSubtitleMenu()
            PlayerIntent.CloseSubtitleMenu -> closeSubtitleMenu()
            is PlayerIntent.SelectSubtitleTrack -> selectSubtitleTrack(intent.index)
        }
    }

    private fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        updatePlaybackPosition()
        showControls()
    }

    // Seeks once input pauses so held keys don't send a stream request per repeat
    private fun seekBy(direction: Int, repeatCount: Int) {
        val ready = _state.value as? PlayerUiState.Ready ?: return
        val start = ready.seekPreviewMs ?: player.currentPosition
        // Forward stops short of the end so overshooting doesn't finish the video, and waits for a known length
        val endLimitMs = player.duration.takeIf { it > 0 }?.let { it - SEEK_END_MARGIN_MS } ?: start
        val target = (start + direction * seekStepMs(repeatCount)).coerceIn(0, maxOf(endLimitMs, start))
        if (target == start) {
            showControls()
            return
        }
        updateReady { copy(seekPreviewMs = target) }
        showControls()

        seekCommitJob?.cancel()
        seekCommitJob = viewModelScope.launch {
            delay(SEEK_COMMIT_DELAY_MS.milliseconds)
            player.seekTo(target)
            updateReady { copy(seekPreviewMs = null) }
            updatePlaybackPosition()
        }
    }

    private fun showControls() {
        updateReady { copy(controlsVisible = true) }
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_HIDE_DELAY_MS.milliseconds)
            updateReady { if (subtitleMenuOpen) this else copy(controlsVisible = false) }
        }
    }

    private fun openSubtitleMenu() {
        hideControlsJob?.cancel()
        updateReady { copy(subtitleMenuOpen = true, controlsVisible = true) }
    }

    private fun closeSubtitleMenu() {
        updateReady { copy(subtitleMenuOpen = false) }
        scheduleHideControls()
    }

    private fun selectSubtitleTrack(index: Int?) {
        val ready = _state.value as? PlayerUiState.Ready ?: return
        item = item.copy(desiredSubtitleIndex = index)
        applyDesiredSubtitleTrack()
        val language = ready.subtitleTracks.firstOrNull { it.index == index }?.language
        if (language != null) {
            viewModelScope.launch { subtitlePreferenceStore.savePreferredLanguage(language) }
        }
        updateReady { copy(selectedSubtitleIndex = index, subtitleMenuOpen = false) }
        scheduleHideControls()
    }

    // Matches by Format.id, not language, so same-language tracks (muxed + external .srt) stay
    // distinguishable. MergingMediaSource prefixes each merged Format.id with "childIndex:", so
    // only the suffix (the id we set via .setId() in toMediaItem, always a plain track.index
    // int with no colon of its own) is compared. Re-run on EVENT_TRACKS_CHANGED since tracks
    // aren't known right after prepare().
    private fun applyDesiredSubtitleTrack() {
        val target = item.desiredSubtitleIndex
        val textGroup = target?.let { idx ->
            player.currentTracks.groups.firstOrNull { group ->
                group.type == C.TRACK_TYPE_TEXT &&
                    (0 until group.length).any { i ->
                        group.getTrackFormat(i).id?.substringAfterLast(':') == idx.toString()
                    }
            }
        }
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textGroup == null)
        if (textGroup != null) {
            builder.addOverride(TrackSelectionOverride(textGroup.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = builder.build()
    }

    // A pending seek is where the user meant to be, and before playback starts there is no position worth saving
    private fun exit() {
        isExiting = true
        stopTick()
        stopUiTick()
        val ready = _state.value as? PlayerUiState.Ready
        val positionMs = ready?.seekPreviewMs ?: player.currentPosition
        cancelPendingSeek()
        if (ready != null) {
            viewModelScope.launch {
                withContext(NonCancellable) { reportProgressSuspend(played = false, positionMs = positionMs) }
            }
        }
        player.stop()
        // Next episode builds its player before this ViewModel is cleared, and two live sessions can't share an id
        mediaSession.release()
    }

    private fun cancelPendingSeek() {
        seekCommitJob?.cancel()
        seekCommitJob = null
        updateReady { copy(seekPreviewMs = null) }
    }

    // Always fetches detail, even with a streamUrl already given, since title and subtitle tracks only come from here
    private fun loadAndPrepare() {
        viewModelScope.launch {
            val detail = repository.getDetail(item.id).getOrNull()
            val preferredLanguage = subtitlePreferenceStore.current()
            // The user may have left while these loaded
            if (isExiting) return@launch
            val loaded = playbackSource(detail, item.routeStreamUrl)
            if (loaded == null) {
                _state.update { PlayerUiState.Error }
                return@launch
            }
            val initialTrack = initialSubtitle(loaded.subtitleTracks, preferredLanguage)
            item = item.copy(source = loaded, desiredSubtitleIndex = initialTrack?.index)
            startPlayback(loaded, item.startPositionMs)
            applyDesiredSubtitleTrack()
            emitReady(loaded, positionMs = item.startPositionMs, selectedIndex = initialTrack?.index)
        }
    }

    private fun startPlayback(source: PlaybackSource, positionMs: Long) {
        player.setMediaItem(source.toMediaItem(), positionMs)
        player.prepare()
        player.play()
    }

    private fun emitReady(source: PlaybackSource, positionMs: Long, selectedIndex: Int?) {
        _state.update {
            PlayerUiState.Ready(
                isPlaying = true,
                positionMs = positionMs,
                durationMs = player.duration.coerceAtLeast(0),
                controlsVisible = true,
                title = source.title,
                episodeLine = source.episodeLine,
                nextEpisode = source.nextEpisode,
                subtitleTracks = source.subtitleTracks.toImmutableList(),
                selectedSubtitleIndex = selectedIndex,
                subtitleMenuOpen = false,
            )
        }
        scheduleHideControls()
    }

    // ExoPlayer's HTTP data source has a static bearer header set at creation time and never
    // sees Ktor's Auth plugin, so a 401 here needs its own refresh-and-retry, once.
    private fun handlePlayerError() {
        val current = item.source
        if (item.hasRetriedAfterAuthError || current == null || !isUnauthorized(player.playerError)) {
            _state.update { PlayerUiState.Error }
            return
        }
        item = item.copy(hasRetriedAfterAuthError = true)
        val resumeMs = (_state.value as? PlayerUiState.Ready)?.seekPreviewMs ?: player.currentPosition
        cancelPendingSeek()
        viewModelScope.launch {
            val refreshed = refreshTokens()
            if (isExiting) return@launch
            if (!refreshed) {
                _state.update { PlayerUiState.Error }
                return@launch
            }
            startPlayback(current, resumeMs)
            val ready = _state.value as? PlayerUiState.Ready
            emitReady(current, positionMs = resumeMs, selectedIndex = ready?.selectedSubtitleIndex)
            applyDesiredSubtitleTrack()
        }
    }

    private fun startTick() {
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_REPORT_INTERVAL_MS.milliseconds)
                reportProgressSuspend(played = false)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun startUiTick() {
        uiTickJob = viewModelScope.launch {
            while (isActive) {
                updatePlaybackPosition()
                delay(UI_TICK_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun stopUiTick() {
        uiTickJob?.cancel()
        uiTickJob = null
    }

    private fun updateBuffering() {
        val isBuffering = player.playbackState == Player.STATE_BUFFERING
        updateReady { copy(isBuffering = isBuffering) }
    }

    private fun updatePlaybackPosition() {
        updateReady {
            copy(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
            )
        }
    }

    // Changes only a Ready state, so Loading and Error are never overwritten
    private inline fun updateReady(crossinline transform: PlayerUiState.Ready.() -> PlayerUiState.Ready) {
        _state.update { s -> if (s is PlayerUiState.Ready) s.transform() else s }
    }

    private fun reportProgress(played: Boolean) {
        viewModelScope.launch { reportProgressSuspend(played) }
    }

    private suspend fun reportProgressSuspend(played: Boolean, positionMs: Long = player.currentPosition) {
        val jfId = (item.source?.streamUrl ?: item.routeStreamUrl)?.substringAfterLast("/") ?: return
        playbackRepository.reportProgress(jfId, positionMs, played)
    }

    override fun onCleared() {
        mediaSession.release()
        player.release()
    }

    companion object {
        private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L
        private const val UI_TICK_INTERVAL_MS = 500L
        private const val CONTROLS_HIDE_DELAY_MS = 3_000L
        private const val SEEK_COMMIT_DELAY_MS = 700L
        private const val SEEK_END_MARGIN_MS = 10_000L
        private const val KEY_ID = "id"
        private const val KEY_STREAM_URL = "streamUrl"
        private const val KEY_POSITION_MS = "positionMs"

        @androidx.annotation.OptIn(UnstableApi::class)
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as StoganetApp
                    val tokenStore = app.services.tokenStore
                    val dataSourceFactory = DataSource.Factory {
                        val token = runBlocking { tokenStore.accessToken() }.orEmpty()
                        DefaultHttpDataSource.Factory()
                            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
                            .createDataSource()
                    }
                    val player = ExoPlayer.Builder(app)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                        .build()
                    val mediaSession = MediaSession.Builder(app, player).build()
                    val refreshHttpClient = app.services.refreshHttpClient
                    PlayerViewModel(
                        savedStateHandle = createSavedStateHandle(),
                        repository = app.services.detailRepository,
                        playbackRepository = app.services.playbackRepository,
                        subtitlePreferenceStore = app.services.subtitlePreferenceStore,
                        player = player,
                        mediaSession = mediaSession,
                        refreshTokens = {
                            val refreshToken = tokenStore.refreshToken()
                            refreshToken != null &&
                                performTokenRefresh(refreshHttpClient, tokenStore, BASE_URL, refreshToken) != null
                        },
                    )
                }
            }
    }
}

private data class NowPlaying(
    val id: String,
    val routeStreamUrl: String?,
    val startPositionMs: Long,
    val source: PlaybackSource? = null,
    val desiredSubtitleIndex: Int? = null,
    val hasRetriedAfterAuthError: Boolean = false,
)

// ExoPlayer wraps the HTTP error in one or more causes, and the cap stops a cause chain that loops back on itself
private fun isUnauthorized(error: Throwable?): Boolean = generateSequence(error) { it.cause }
    .take(MAX_CAUSE_CHAIN_DEPTH)
    .any { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == UNAUTHORIZED_STATUS_CODE }

private data class SeekTier(val fromRepeat: Int, val stepMs: Long)

// Android TV repeats a held key about 20 times per second, so each tier starts roughly a second later
private val SEEK_TIERS = listOf(
    SeekTier(fromRepeat = 0, stepMs = 10_000L),
    SeekTier(fromRepeat = 20, stepMs = 30_000L),
    SeekTier(fromRepeat = 40, stepMs = 60_000L),
    SeekTier(fromRepeat = 60, stepMs = 120_000L),
)

internal fun seekStepMs(repeatCount: Int): Long = SEEK_TIERS.last { repeatCount >= it.fromRepeat }.stepMs

private const val UNAUTHORIZED_STATUS_CODE = 401
private const val MAX_CAUSE_CHAIN_DEPTH = 10
