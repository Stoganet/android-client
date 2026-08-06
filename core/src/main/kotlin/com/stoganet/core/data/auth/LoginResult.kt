package com.stoganet.core.data.auth

import com.stoganet.core.api.model.TokenPair

sealed interface LoginResult {
    data class Success(val tokens: TokenPair) : LoginResult
    data object InvalidCredentials : LoginResult
    data object NetworkError : LoginResult
}
