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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DetailRepository,
    private val playbackRepository: PlaybackRepository,
    private val subtitlePreferenceStore: SubtitlePreferenceStore,
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val refreshTokens: suspend () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val nowPlaying = MutableStateFlow(
        NowPlaying(
            id = checkNotNull(savedStateHandle[KEY_ID]),
            startPositionMs = savedStateHandle[KEY_POSITION_MS] ?: 0L,
        ),
    )
    private val interactions = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_OLDEST)
    private val playback: Job = viewModelScope.launch { nowPlaying.collectLatest { play(it) } }

    init {
        viewModelScope.launch {
            interactions.collectLatest {
                delay(CONTROLS_HIDE_DELAY_MS.milliseconds)
                updateReady { if (subtitleMenuOpen) this else copy(controlsVisible = false) }
            }
        }
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Exit -> exit()
            PlayerIntent.PlayNext -> playNext()
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            is PlayerIntent.SeekBackward -> seekBy(direction = -1, repeatCount = intent.repeatCount)
            is PlayerIntent.SeekForward -> seekBy(direction = 1, repeatCount = intent.repeatCount)
            PlayerIntent.ShowControls -> showControls()
            PlayerIntent.OpenSubtitleMenu -> openSubtitleMenu()
            PlayerIntent.CloseSubtitleMenu -> closeSubtitleMenu()
            is PlayerIntent.SelectSubtitleTrack -> selectSubtitleTrack(intent.index)
        }
    }

    private suspend fun play(item: NowPlaying) {
        _state.update { PlayerUiState.Loading }
        val detail = repository.getDetail(item.id).getOrNull()
        val preferredLanguage = subtitlePreferenceStore.current()
        val source = playbackSource(detail)
        if (source == null) {
            _state.update { PlayerUiState.Error }
            return
        }
        try {
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) { listenToPlayer(source) }
                launch { commitSeeks() }
                startPlayback(source, item.startPositionMs)
                val initialTrack = initialSubtitle(source.subtitleTracks, preferredLanguage)
                emitReady(source, positionMs = item.startPositionMs, selectedIndex = initialTrack?.index)
                applySelectedSubtitleTrack()
            }
        } finally {
            leave(source)
        }
    }

    private suspend fun listenToPlayer(source: PlaybackSource): Nothing = coroutineScope {
        var hasRetriedAfterAuthError = false
        val playing = MutableStateFlow(false)
        launch {
            playing.collectLatest { isPlaying ->
                while (isPlaying) {
                    delay(PROGRESS_REPORT_INTERVAL_MS.milliseconds)
                    reportProgress(source, played = false)
                }
            }
        }
        launch {
            playing.collectLatest { isPlaying ->
                while (isPlaying) {
                    updatePlaybackPosition()
                    delay(UI_TICK_INTERVAL_MS.milliseconds)
                }
            }
        }
        player.listenTo(
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAYER_ERROR,
            Player.EVENT_TRACKS_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
        ) { events ->
            if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                if (hasRetriedAfterAuthError || !isUnauthorized(playerError)) {
                    _state.update { PlayerUiState.Error }
                } else {
                    hasRetriedAfterAuthError = true
                    val resumeMs = (_state.value as? PlayerUiState.Ready)?.seekPreviewMs ?: currentPosition
                    cancelPendingSeek()
                    launch { retryAfterRefresh(source, resumeMs) }
                }
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                applySelectedSubtitleTrack()
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                updatePlaybackPosition()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                playing.value = isPlaying
                if (!isPlaying) {
                    updatePlaybackPosition()
                    launch { reportProgress(source, played = false) }
                }
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                updateBuffering()
                if (playbackState == Player.STATE_ENDED) launch { reportProgress(source, played = true) }
            }
        }
    }

    // ExoPlayer's HTTP data source has a static bearer header set at creation time and never
    // sees Ktor's Auth plugin, so a 401 here needs its own refresh-and-retry once
    private suspend fun retryAfterRefresh(source: PlaybackSource, resumeMs: Long) {
        if (!refreshTokens()) {
            _state.update { PlayerUiState.Error }
            return
        }
        startPlayback(source, resumeMs)
        val ready = _state.value as? PlayerUiState.Ready
        emitReady(source, positionMs = resumeMs, selectedIndex = ready?.selectedSubtitleIndex)
        applySelectedSubtitleTrack()
    }

    // Seeks once input pauses so held keys don't send a stream request per repeat
    private suspend fun commitSeeks() {
        _state.map { (it as? PlayerUiState.Ready)?.seekPreviewMs }
            .distinctUntilChanged()
            .collectLatest { target ->
                if (target == null) return@collectLatest
                delay(SEEK_COMMIT_DELAY_MS.milliseconds)
                player.seekTo(target)
                updateReady { copy(seekPreviewMs = null) }
                updatePlaybackPosition()
            }
    }

    private fun leave(source: PlaybackSource) {
        val positionMs = (_state.value as? PlayerUiState.Ready)?.seekPreviewMs ?: player.currentPosition
        player.stop()
        viewModelScope.launch {
            withContext(NonCancellable) { reportProgress(source, played = false, positionMs = positionMs) }
        }
    }

    private fun exit() {
        playback.cancel()
        mediaSession.release()
    }

    private fun playNext() {
        val next = (_state.value as? PlayerUiState.Ready)?.nextEpisode ?: return
        if (!playback.isActive) return
        savedStateHandle[KEY_ID] = next.id
        savedStateHandle[KEY_POSITION_MS] = next.positionMs
        nowPlaying.value = NowPlaying(id = next.id, startPositionMs = next.positionMs)
    }

    private fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        updatePlaybackPosition()
        showControls()
    }

    private fun seekBy(direction: Int, repeatCount: Int) {
        val ready = _state.value as? PlayerUiState.Ready ?: return
        val start = ready.seekPreviewMs ?: player.currentPosition
        // Forward stops short of the end so overshooting doesn't finish the video, and waits for a known length
        val endLimitMs = player.duration.takeIf { it > 0 }?.let { it - SEEK_END_MARGIN_MS } ?: start
        val target = (start + direction * seekStepMs(repeatCount)).coerceIn(0, maxOf(endLimitMs, start))
        if (target != start) updateReady { copy(seekPreviewMs = target) }
        showControls()
    }

    private fun cancelPendingSeek() {
        updateReady { copy(seekPreviewMs = null) }
    }

    private fun showControls() {
        updateReady { copy(controlsVisible = true) }
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        interactions.tryEmit(Unit)
    }

    private fun openSubtitleMenu() {
        updateReady { copy(subtitleMenuOpen = true, controlsVisible = true) }
    }

    private fun closeSubtitleMenu() {
        updateReady { copy(subtitleMenuOpen = false) }
        scheduleHideControls()
    }

    private fun selectSubtitleTrack(index: Int?) {
        val ready = _state.value as? PlayerUiState.Ready ?: return
        updateReady { copy(selectedSubtitleIndex = index, subtitleMenuOpen = false) }
        applySelectedSubtitleTrack()
        val language = ready.subtitleTracks.firstOrNull { it.index == index }?.language
        if (language != null) {
            viewModelScope.launch { subtitlePreferenceStore.savePreferredLanguage(language) }
        }
        scheduleHideControls()
    }

    // Matches by Format.id, not language, so same-language tracks (muxed + external .srt) stay
    // distinguishable. MergingMediaSource prefixes each merged Format.id with "childIndex:", so
    // only the suffix (the id we set via .setId() in toMediaItem, always a plain track.index
    // int with no colon of its own) is compared. Re-run on EVENT_TRACKS_CHANGED since tracks
    // aren't known right after prepare().
    private fun applySelectedSubtitleTrack() {
        val target = (_state.value as? PlayerUiState.Ready)?.selectedSubtitleIndex
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

    private suspend fun reportProgress(
        source: PlaybackSource,
        played: Boolean,
        positionMs: Long = player.currentPosition,
    ) {
        val jfId = source.streamUrl.substringAfterLast("/")
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

private data class NowPlaying(val id: String, val startPositionMs: Long)

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
