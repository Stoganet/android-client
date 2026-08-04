package com.stoganet.tv.ui.detail

sealed interface DetailIntent {
    data object Retry : DetailIntent
    data class SelectSeason(val seasonNumber: Int) : DetailIntent
}
