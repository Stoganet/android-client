package com.stoganet.tv.ui.player

import androidx.compose.runtime.Immutable

sealed interface PlayerUiState {
    @Immutable data object Loading : PlayerUiState

    @Immutable data object Error : PlayerUiState

    @Immutable data object Ready : PlayerUiState
}
