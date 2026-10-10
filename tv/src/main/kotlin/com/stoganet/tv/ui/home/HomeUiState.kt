package com.stoganet.tv.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class HomeSectionUiState(
    val id: String,
    @param:StringRes val titleRes: Int,
    val items: ImmutableList<HomeItemUiState>,
    val hasMore: Boolean,
    val seeMoreRoute: String? = null,
)

@Immutable
data class HomeItemUiState(
    val id: String,
    val title: String,
    val year: Int,
    val overview: String,
    val contentDescription: String,
    val backdropUrl: String? = null,
    val thumbUrl: String? = null,
    val thumbHasLogo: Boolean = false,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(val sections: ImmutableList<HomeSectionUiState>, val focusedItemKey: String? = null) :
        HomeUiState
    data object Error : HomeUiState
}
