package com.stoganet.tv.ui.auth

sealed interface QuickConnectIntent {
    data object Retry : QuickConnectIntent
}
