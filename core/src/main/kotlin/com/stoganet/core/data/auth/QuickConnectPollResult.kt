package com.stoganet.core.data.auth

import com.stoganet.core.api.model.TokenPair

sealed interface QuickConnectPollResult {
    data class Success(val tokens: TokenPair) : QuickConnectPollResult
    data object Pending : QuickConnectPollResult
    data object Expired : QuickConnectPollResult
}
