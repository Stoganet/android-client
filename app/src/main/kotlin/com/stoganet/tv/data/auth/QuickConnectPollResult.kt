package com.stoganet.tv.data.auth

import com.stoganet.tv.api.model.TokenPair

sealed interface QuickConnectPollResult {
    data class Success(val tokens: TokenPair) : QuickConnectPollResult
    data object Pending : QuickConnectPollResult
    data object Expired : QuickConnectPollResult
}
