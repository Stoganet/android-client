package com.stoganet.tv.ui.episode

sealed interface EpisodeIntent {
    data object Retry : EpisodeIntent
}
