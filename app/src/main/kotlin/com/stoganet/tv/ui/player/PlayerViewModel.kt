package com.stoganet.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.data.detail.DetailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlayerViewModel(
    private val id: String,
    private val repository: DetailRepository,
    val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val streamUrl: String? = null,
    private val positionMs: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _state.update { PlayerUiState.Error }
            }
        })
        loadAndPrepare()
    }

    private fun loadAndPrepare() {
        val url = streamUrl
        if (url != null) {
            player.setMediaItem(MediaItem.fromUri(url), positionMs)
            player.prepare()
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
                    player.setMediaItem(MediaItem.fromUri(play.streamUrl), positionMs)
                    player.prepare()
                    _state.update { PlayerUiState.Ready }
                }
                .onFailure { _state.update { PlayerUiState.Error } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaSession.release()
        player.release()
    }

    companion object {
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
                        player = player,
                        mediaSession = mediaSession,
                        streamUrl = streamUrl,
                        positionMs = positionMs,
                    )
                }
            }
    }
}
