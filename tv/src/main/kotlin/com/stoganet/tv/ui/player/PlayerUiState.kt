package com.stoganet.tv.ui.player

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SubtitleTrackUi(val index: Int, val language: String, val title: String, val isDefault: Boolean)

sealed interface PlayerUiState {
    @Immutable data object Loading : PlayerUiState

    @Immutable data object Error : PlayerUiState

    @Immutable
    data class Ready(
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val controlsVisible: Boolean,
        val subtitleTracks: ImmutableList<SubtitleTrackUi> = persistentListOf(),
        val selectedSubtitleIndex: Int? = null,
        val subtitleMenuOpen: Boolean = false,
    ) : PlayerUiState
}
