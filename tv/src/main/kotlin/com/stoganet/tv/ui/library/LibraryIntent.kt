package com.stoganet.tv.ui.library

sealed interface LibraryIntent {
    data object Retry : LibraryIntent
    data object LoadMore : LibraryIntent
    data class ItemClicked(val id: String) : LibraryIntent
}
