package com.stoganet.tv.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
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
    private val id: String,
    private val repository: DetailRepository,
    private val playbackRepository: PlaybackRepository,
    private val subtitlePreferenceStore: SubtitlePreferenceStore,
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val refreshTokens: suspend () -> Boolean,
    private val streamUrl: String? = null,
    private val positionMs: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentStreamUrl: String? = streamUrl
    private var currentSubtitleTracks: List<SubtitleTrackUi> = emptyList()
    private var tickJob: Job? = null
    private var uiTickJob: Job? = null
    private var hideControlsJob: Job? = null
    private var isExiting = false
    private var hasRetriedAfterAuthError = false

    init {
        viewModelScope.launch {
            player.listenTo(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAYER_ERROR,
            ) { events ->
                if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                    handlePlayerError()
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
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && playbackState == Player.STATE_ENDED) {
                    reportProgress(played = true)
                }
            }
        }
        loadAndPrepare()
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Exit -> exit()
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            PlayerIntent.SeekBackward -> seekBy(-SEEK_INCREMENT_MS)
            PlayerIntent.SeekForward -> seekBy(SEEK_INCREMENT_MS)
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

    private fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
        updatePlaybackPosition()
        showControls()
    }

    private fun showControls() {
        _state.update { s -> if (s is PlayerUiState.Ready) s.copy(controlsVisible = true) else s }
        scheduleHideControls()
    }

    private fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_HIDE_DELAY_MS)
            _state.update { s ->
                if (s is PlayerUiState.Ready && !s.subtitleMenuOpen) s.copy(controlsVisible = false) else s
            }
        }
    }

    private fun openSubtitleMenu() {
        hideControlsJob?.cancel()
        _state.update { s ->
            if (s is PlayerUiState.Ready) s.copy(subtitleMenuOpen = true, controlsVisible = true) else s
        }
    }

    private fun closeSubtitleMenu() {
        _state.update { s -> if (s is PlayerUiState.Ready) s.copy(subtitleMenuOpen = false) else s }
        scheduleHideControls()
    }

    private fun selectSubtitleTrack(index: Int?) {
        val ready = _state.value as? PlayerUiState.Ready ?: return
        applySubtitleTrack(ready.subtitleTracks, index)
        val language = ready.subtitleTracks.firstOrNull { it.index == index }?.language
        if (language != null) {
            viewModelScope.launch { subtitlePreferenceStore.savePreferredLanguage(language) }
        }
        _state.update { s ->
            if (s is PlayerUiState.Ready) s.copy(selectedSubtitleIndex = index, subtitleMenuOpen = false) else s
        }
        scheduleHideControls()
    }

    private fun applySubtitleTrack(tracks: List<SubtitleTrackUi>, index: Int?) {
        val track = tracks.firstOrNull { it.index == index }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, track == null)
            .setPreferredTextLanguage(track?.language)
            .build()
    }

    private fun exit() {
        isExiting = true
        stopTick()
        stopUiTick()
        viewModelScope.launch {
            withContext(NonCancellable) { reportProgressSuspend(played = false) }
        }
        player.stop()
    }

    // Always fetches detail, even with a streamUrl already given, since subtitle_tracks only
    // comes from here. Falls back to the given streamUrl so episodes/resume/failed fetches still play.
    private fun loadAndPrepare() {
        viewModelScope.launch {
            val play = repository.getDetail(id).getOrNull()?.play
            val url = streamUrl ?: play?.streamUrl
            if (url == null) {
                _state.update { PlayerUiState.Error }
                return@launch
            }
            val tracks = play?.subtitleTracks.orEmpty().map {
                SubtitleTrackUi(it.index, it.language, it.title, it.isDefault)
            }
            currentStreamUrl = url
            currentSubtitleTracks = tracks
            player.setMediaItem(buildMediaItem(url, tracks), positionMs)
            player.prepare()
            player.play()
            val preferredLanguage = subtitlePreferenceStore.current()
            val initialTrack = tracks.firstOrNull { it.language == preferredLanguage }
                ?: tracks.firstOrNull { it.isDefault }
            applySubtitleTrack(tracks, initialTrack?.index)
            emitReady(positionMs = positionMs, tracks = tracks, selectedIndex = initialTrack?.index)
        }
    }

    private fun buildMediaItem(streamUrl: String, tracks: List<SubtitleTrackUi>): MediaItem {
        val subtitleConfigs = tracks.map { track ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse("$streamUrl/subtitles/${track.index}"))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(track.language)
                .setLabel(track.title)
                .build()
        }
        return MediaItem.Builder().setUri(streamUrl).setSubtitleConfigurations(subtitleConfigs).build()
    }

    private fun emitReady(positionMs: Long, tracks: List<SubtitleTrackUi>, selectedIndex: Int?) {
        _state.update {
            PlayerUiState.Ready(
                isPlaying = true,
                positionMs = positionMs,
                durationMs = player.duration.coerceAtLeast(0),
                controlsVisible = true,
                subtitleTracks = tracks.toImmutableList(),
                selectedSubtitleIndex = selectedIndex,
                subtitleMenuOpen = false,
            )
        }
        scheduleHideControls()
    }

    // ExoPlayer's HTTP data source has a static bearer header set at creation time and never
    // sees Ktor's Auth plugin, so a 401 here needs its own refresh-and-retry, once.
    private fun handlePlayerError() {
        val url = currentStreamUrl
        if (hasRetriedAfterAuthError || url == null || !isUnauthorized(player.playerError)) {
            _state.update { PlayerUiState.Error }
            return
        }
        hasRetriedAfterAuthError = true
        val resumeMs = player.currentPosition
        viewModelScope.launch {
            if (!refreshTokens()) {
                _state.update { PlayerUiState.Error }
                return@launch
            }
            player.setMediaItem(buildMediaItem(url, currentSubtitleTracks), resumeMs)
            player.prepare()
            player.play()
            val ready = _state.value as? PlayerUiState.Ready
            emitReady(
                positionMs = resumeMs,
                tracks = currentSubtitleTracks,
                selectedIndex = ready?.selectedSubtitleIndex,
            )
            applySubtitleTrack(currentSubtitleTracks, ready?.selectedSubtitleIndex)
        }
    }

    private fun isUnauthorized(error: Throwable?): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            if (cause is HttpDataSource.InvalidResponseCodeException &&
                cause.responseCode == UNAUTHORIZED_STATUS_CODE
            ) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
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
                delay(UI_TICK_INTERVAL_MS)
            }
        }
    }

    private fun stopUiTick() {
        uiTickJob?.cancel()
        uiTickJob = null
    }

    private fun updatePlaybackPosition() {
        _state.update { s ->
            if (s is PlayerUiState.Ready) {
                s.copy(
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0),
                )
            } else {
                s
            }
        }
    }

    private fun reportProgress(played: Boolean) {
        viewModelScope.launch { reportProgressSuspend(played) }
    }

    private suspend fun reportProgressSuspend(played: Boolean) {
        val jfId = currentStreamUrl?.substringAfterLast("/") ?: return
        playbackRepository.reportProgress(jfId, player.currentPosition, played)
    }

    override fun onCleared() {
        super.onCleared()
        mediaSession.release()
        player.release()
    }

    companion object {
        private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L
        private const val UI_TICK_INTERVAL_MS = 500L
        private const val CONTROLS_HIDE_DELAY_MS = 3_000L
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val UNAUTHORIZED_STATUS_CODE = 401
        private const val MAX_CAUSE_CHAIN_DEPTH = 10

        @androidx.annotation.OptIn(UnstableApi::class)
        fun factory(id: String, streamUrl: String? = null, positionMs: Long = 0L): ViewModelProvider.Factory =
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
                        id = id,
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
                        streamUrl = streamUrl,
                        positionMs = positionMs,
                    )
                }
            }
    }
}
