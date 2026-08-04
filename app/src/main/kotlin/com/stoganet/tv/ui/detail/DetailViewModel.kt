package com.stoganet.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.api.model.Episode
import com.stoganet.tv.api.model.LibraryDetail
import com.stoganet.tv.api.model.MediaState
import com.stoganet.tv.api.model.ResumeInfo
import com.stoganet.tv.api.model.Season
import com.stoganet.tv.data.detail.DetailRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailViewModel(private val id: String, private val repository: DetailRepository) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        loadDetail()
    }

    fun onIntent(intent: DetailIntent) {
        when (intent) {
            DetailIntent.Retry -> {
                if (_state.value is DetailUiState.Loading) return
                loadDetail()
            }

            is DetailIntent.SelectSeason -> selectSeason(intent.seasonNumber)
        }
    }

    private fun loadDetail() {
        _state.update { DetailUiState.Loading }
        viewModelScope.launch {
            repository.getDetail(id)
                .onSuccess { detail -> _state.update { detail.toUiState() } }
                .onFailure { _state.update { DetailUiState.Error } }
        }
    }

    private fun selectSeason(seasonNumber: Int) {
        val current = _state.value as? DetailUiState.Content ?: return
        if (current.selectedSeason == seasonNumber) return
        val previousSeason = current.selectedSeason
        _state.update {
            (it as DetailUiState.Content).copy(
                selectedSeason = seasonNumber,
                episodes = persistentListOf(),
            )
        }
        viewModelScope.launch {
            repository.getEpisodes(id, seasonNumber)
                .onSuccess { episodes ->
                    _state.update { state ->
                        val s = state as? DetailUiState.Content ?: return@update state
                        if (s.selectedSeason != seasonNumber) return@update state
                        s.copy(episodes = episodes.map { it.toUiState() }.toImmutableList())
                    }
                }
                .onFailure {
                    _state.update { state ->
                        val s = state as? DetailUiState.Content ?: return@update state
                        if (s.selectedSeason != seasonNumber) return@update state
                        s.copy(selectedSeason = previousSeason, episodes = persistentListOf())
                    }
                }
        }
    }

    private fun LibraryDetail.toUiState() = DetailUiState.Content(
        title = title,
        year = year,
        mediaType = type,
        backdropUrl = backdrop,
        overview = overview,
        genres = genres.toImmutableList(),
        runtime = formatRuntime(runtime),
        cast = cast.map { CastMemberUiState(it.name, it.role) }.toImmutableList(),
        seasons = seasons.map { it.toUiState() }.toImmutableList(),
        resume = resume?.toUiState(),
        streamUrl = play?.streamUrl,
        isPlayable = state == MediaState.PLAYABLE && play != null,
    )

    companion object {
        fun formatRuntime(minutes: Int): String {
            if (minutes <= 0) return ""
            val h = minutes / 60
            val m = minutes % 60
            return when {
                h == 0 -> "${m}m"
                m == 0 -> "${h}h"
                else -> "${h}h ${m}m"
            }
        }

        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                DetailViewModel(id = id, repository = app.services.detailRepository)
            }
        }
    }
}

private fun Season.toUiState() = SeasonUiState(
    number = number,
    name = name,
    episodeCount = episodeCount,
    posterUrl = poster,
    overview = overview,
)

private fun Episode.toUiState() = EpisodeUiState(
    id = id,
    number = number,
    title = title,
    overview = overview,
    runtimeMinutes = runtime,
    thumbnailUrl = thumbnail,
    streamUrl = play?.streamUrl,
    positionMs = progress?.positionMs ?: 0L,
    played = progress?.played ?: false,
)

private fun ResumeInfo.toUiState() = ResumeUiState(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeId = episodeId,
    title = title,
    streamUrl = play.streamUrl,
    positionMs = progress.positionMs,
)
