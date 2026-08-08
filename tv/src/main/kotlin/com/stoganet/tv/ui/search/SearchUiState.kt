package com.stoganet.tv.ui.search

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class SearchResultUiState(val id: String, val posterUrl: String, val contentDescription: String)

sealed interface SearchUiState {
    val query: String

    @Immutable
    data class Empty(override val query: String = "") : SearchUiState

    @Immutable
    data class Loading(override val query: String) : SearchUiState

    @Immutable
    data class Results(override val query: String, val items: ImmutableList<SearchResultUiState>) : SearchUiState

    @Immutable
    data class NoResults(override val query: String) : SearchUiState

    @Immutable
    data class Error(override val query: String) : SearchUiState
}
