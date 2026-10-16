package com.stoganet.tv

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data object SignedIn : MainActivityUiState
    data object SignedOut : MainActivityUiState
}
