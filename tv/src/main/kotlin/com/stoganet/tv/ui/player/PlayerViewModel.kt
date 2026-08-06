package com.stoganet.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listenTo
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.playback.PlaybackRepository
import com.stoganet.tv.StoganetApp
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
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val streamUrl: String? = null,
    private val positionMs: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentStreamUrl: String? = streamUrl
    private var tickJob: Job? = null
    private var isExiting = false

    init {
        viewModelScope.launch {
            player.listenTo(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAYER_ERROR,
            ) { events ->
                if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                    _state.update { PlayerUiState.Error }
                }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    if (isPlaying) {
                        startTick()
                    } else {
                        stopTick()
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
        }
    }

    private fun exit() {
        isExiting = true
        stopTick()
        viewModelScope.launch {
            withContext(NonCancellable) { reportProgressSuspend(played = false) }
        }
        player.stop()
    }

    private fun loadAndPrepare() {
        val url = streamUrl
        if (url != null) {
            player.setMediaItem(MediaItem.fromUri(url), positionMs)
            player.prepare()
            player.play()
            _state.update { PlayerUiState.Ready }
            return
        }
        viewModelScope.launch {
            repository.getDetail(id)
                .onSuccess { detail ->
                    val play = detail.play
                    if (play == null) {
                        _state.update { PlayerUiState.Error }
                        return@onSuccess
                    }
                    currentStreamUrl = play.streamUrl
                    player.setMediaItem(MediaItem.fromUri(play.streamUrl), positionMs)
                    player.prepare()
                    player.play()
                    _state.update { PlayerUiState.Ready }
                }
                .onFailure { _state.update { PlayerUiState.Error } }
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
                    PlayerViewModel(
                        id = id,
                        repository = app.services.detailRepository,
                        playbackRepository = app.services.playbackRepository,
                        player = player,
                        mediaSession = mediaSession,
                        streamUrl = streamUrl,
                        positionMs = positionMs,
                    )
                }
            }
    }
}
