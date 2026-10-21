package com.stoganet.tv.ui.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.playback.progressFraction
import com.stoganet.core.data.playback.resumePositionMs
import com.stoganet.core.util.formatAirDate
import com.stoganet.core.util.formatRuntime
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.ui.detail.CastMemberUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MS = 5_000L
private const val MS_PER_MINUTE = 60_000L

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeViewModel(private val id: String, private val repository: DetailRepository) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val state: StateFlow<EpisodeUiState> = retries
        .map { true }
        .onStart { emit(false) }
        .flatMapLatest { isRetry -> loadDetail(showLoading = isRetry) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EpisodeUiState.Loading)

    fun onIntent(intent: EpisodeIntent) {
        when (intent) {
            EpisodeIntent.Retry -> if (state.value !is EpisodeUiState.Loading) retries.tryEmit(Unit)
        }
    }

    private fun loadDetail(showLoading: Boolean): Flow<EpisodeUiState> = flow {
        if (showLoading) emit(EpisodeUiState.Loading)
        var loaded = false
        repository.detail(id).collect { result ->
            result
                .onSuccess {
                    loaded = true
                    emit(it.toUiState())
                }
                .onFailure { if (!loaded) emit(EpisodeUiState.Error) }
        }
    }

    private fun LibraryDetail.toUiState() = EpisodeUiState.Content(
        playId = id,
        title = title,
        runtime = formatRuntime(runtime),
        overview = overview,
        cast = cast.map { CastMemberUiState(it.name, it.role) }.toImmutableList(),
        isPlayable = play != null && state == MediaState.PLAYABLE,
        seriesTitle = episode?.series?.title,
        seasonNumber = episode?.seasonNumber,
        episodeNumber = episode?.number,
        airDate = formatAirDate(episode?.airDate),
        backdropUrl = backdrop,
        stillUrl = poster,
        positionMs = progress.resumePositionMs(),
        progressFraction = progressFraction(progress.resumePositionMs(), runtime * MS_PER_MINUTE),
        played = progress?.played ?: false,
        nextEpisodeId = episode?.next?.id,
    )

    companion object {
        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                EpisodeViewModel(id = id, repository = app.services.detailRepository)
            }
        }
    }
}
