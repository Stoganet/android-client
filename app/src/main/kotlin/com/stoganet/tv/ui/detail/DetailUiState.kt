package com.stoganet.tv.ui.detail

import androidx.compose.runtime.Immutable
import com.stoganet.tv.api.model.MediaType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class CastMemberUiState(val name: String, val role: String)

@Immutable
data class SeasonUiState(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String?,
    val overview: String?,
)

@Immutable
data class ResumeUiState(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: String,
    val title: String,
    val streamUrl: String,
    val positionMs: Long,
)

@Immutable
data class EpisodeUiState(
    val id: String,
    val number: Int,
    val title: String,
    val overview: String?,
    val runtimeMinutes: Int?,
    val thumbnailUrl: String?,
    val streamUrl: String?,
    val positionMs: Long,
    val played: Boolean,
)

sealed interface DetailUiState {
    @Immutable data object Loading : DetailUiState

    @Immutable data object Error : DetailUiState

    @Immutable
    data class Content(
        val title: String,
        val year: Int,
        val mediaType: MediaType,
        val backdropUrl: String?,
        val overview: String,
        val genres: ImmutableList<String>,
        val runtime: String,
        val cast: ImmutableList<CastMemberUiState>,
        val seasons: ImmutableList<SeasonUiState>,
        val resume: ResumeUiState?,
        val streamUrl: String?,
        val isPlayable: Boolean,
        val episodes: ImmutableList<EpisodeUiState> = persistentListOf(),
        val selectedSeason: Int? = null,
    ) : DetailUiState
}
