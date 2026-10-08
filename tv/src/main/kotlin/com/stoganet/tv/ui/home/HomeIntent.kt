package com.stoganet.tv.ui.home

sealed interface HomeIntent {
    data object Retry : HomeIntent
    data class ItemClicked(val key: String) : HomeIntent
}
