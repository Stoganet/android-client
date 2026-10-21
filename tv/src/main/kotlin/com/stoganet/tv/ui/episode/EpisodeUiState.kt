package com.stoganet.tv.ui.episode

import androidx.compose.runtime.Immutable
import com.stoganet.tv.ui.detail.CastMemberUiState
import kotlinx.collections.immutable.ImmutableList

sealed interface EpisodeUiState {
    @Immutable data object Loading : EpisodeUiState

    @Immutable data object Error : EpisodeUiState

    @Immutable
    data class Content(
        val playId: String,
        val title: String,
        val runtime: String,
        val overview: String,
        val cast: ImmutableList<CastMemberUiState>,
        val isPlayable: Boolean,
        val seriesTitle: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val airDate: String? = null,
        val backdropUrl: String? = null,
        val stillUrl: String? = null,
        val positionMs: Long = 0L,
        val progressFraction: Float = 0f,
        val played: Boolean = false,
        val nextEpisodeId: String? = null,
    ) : EpisodeUiState
}
