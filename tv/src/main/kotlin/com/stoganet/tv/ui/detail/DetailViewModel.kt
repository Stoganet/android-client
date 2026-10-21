package com.stoganet.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.ResumeInfo
import com.stoganet.core.api.model.Season
import com.stoganet.core.data.detail.DetailRepository
import com.stoganet.core.data.playback.resumePositionMs
import com.stoganet.core.data.search.SearchRepository
import com.stoganet.core.util.UserMessageCenter
import com.stoganet.core.util.formatRuntime
import com.stoganet.tv.R
import com.stoganet.tv.StoganetApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

private data class SeasonEpisodes(val season: Int?, val episodes: ImmutableList<EpisodeUiState>)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val id: String,
    private val repository: DetailRepository,
    private val searchRepository: SearchRepository,
    private val userMessageCenter: UserMessageCenter,
) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val seasonPicks = MutableSharedFlow<Int>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var shownEpisodes = SeasonEpisodes(season = null, episodes = persistentListOf())

    private val requested = MutableStateFlow(false)

    private val detail: Flow<DetailUiState> = retries
        .map { true }
        .onStart { emit(false) }
        .flatMapLatest { isRetry -> loadDetail(showLoading = isRetry) }

    private val episodes: Flow<SeasonEpisodes> = seasonPicks
        .flatMapLatest { season -> loadEpisodes(season, previous = shownEpisodes) }
        .onStart { emit(shownEpisodes) }
        .onEach { shownEpisodes = it }

    val state: StateFlow<DetailUiState> = combine(detail, episodes, requested) { detail, episodes, requested ->
        if (detail !is DetailUiState.Content) return@combine detail
        detail.copy(
            selectedSeason = episodes.season,
            episodes = episodes.episodes,
            mediaState = if (requested && detail.mediaState == MediaState.REQUESTABLE) {
                MediaState.DOWNLOADING
            } else {
                detail.mediaState
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailUiState.Loading)

    fun onIntent(intent: DetailIntent) {
        when (intent) {
            DetailIntent.Retry -> if (state.value !is DetailUiState.Loading) retries.tryEmit(Unit)
            is DetailIntent.SelectSeason -> selectSeason(intent.seasonNumber)
            DetailIntent.RequestMovie -> requestMovie()
        }
    }

    private fun selectSeason(seasonNumber: Int) {
        if (seasonNumber != shownEpisodes.season) seasonPicks.tryEmit(seasonNumber)
    }

    private fun requestMovie() {
        val current = state.value
        if (current !is DetailUiState.Content || current.mediaState != MediaState.REQUESTABLE) return
        requested.value = true
        viewModelScope.launch {
            searchRepository.requestMovie(id).onFailure {
                requested.value = false
                userMessageCenter.show(R.string.detail_request_failed_message)
            }
        }
    }

    private fun loadDetail(showLoading: Boolean): Flow<DetailUiState> = flow {
        if (showLoading) emit(DetailUiState.Loading)
        var loaded = false
        repository.detail(id).collect { result ->
            result
                .onSuccess {
                    loaded = true
                    emit(it.toUiState())
                }
                .onFailure { if (!loaded) emit(DetailUiState.Error) }
        }
    }

    private fun loadEpisodes(season: Int, previous: SeasonEpisodes): Flow<SeasonEpisodes> = flow {
        // The same season again means the pipeline restarted, so its list stays until the new one arrives
        var loaded = season == previous.season
        if (!loaded) emit(SeasonEpisodes(season, persistentListOf()))
        emitAll(
            repository.episodes(id, season).transformWhile { result ->
                result.fold(
                    onSuccess = { list ->
                        loaded = true
                        emit(SeasonEpisodes(season, list.map { it.toUiState() }.toImmutableList()))
                        true
                    },
                    onFailure = {
                        if (!loaded) emit(SeasonEpisodes(previous.season, persistentListOf()))
                        loaded
                    },
                )
            },
        )
    }

    private fun LibraryDetail.toUiState() = DetailUiState.Content(
        id = this@DetailViewModel.id,
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
        start = start?.toUiState(),
        isPlayable = state == MediaState.PLAYABLE && play != null,
        mediaState = state,
        positionMs = progress.resumePositionMs(),
    )

    companion object {
        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                DetailViewModel(
                    id = id,
                    repository = app.services.detailRepository,
                    searchRepository = app.services.searchRepository,
                    userMessageCenter = app.services.userMessageCenter,
                )
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
)

private fun ResumeInfo.toUiState() = ResumeUiState(
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeId = episodeId,
    title = title,
    positionMs = progress.positionMs,
)
