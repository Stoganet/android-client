package com.stoganet.tv.ui.search

sealed interface SearchIntent {
    data class QueryChanged(val text: String) : SearchIntent
    data object Submit : SearchIntent
}
