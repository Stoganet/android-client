package com.stoganet.tv.ui.home

sealed interface HomeIntent {
    data object Retry : HomeIntent
}
