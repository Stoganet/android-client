package com.stoganet.core.data.auth

import com.stoganet.core.api.model.QuickConnectStartResponse
import com.stoganet.core.data.net.StoganetApi

class AuthRepository(private val api: StoganetApi, private val tokenStore: TokenStore) {

    suspend fun login(username: String, password: String, deviceLabel: String?): Result<LoginResult> = runCatching {
        api.login(username, password, deviceLabel)
    }

    suspend fun startQuickConnect(): Result<QuickConnectStartResponse> = runCatching {
        api.startQuickConnect()
    }

    suspend fun pollQuickConnect(pollToken: String): Result<QuickConnectPollResult> = runCatching {
        api.pollQuickConnect(pollToken)
    }

    suspend fun logout(refreshToken: String): Result<Unit> = runCatching {
        api.logout(refreshToken)
        tokenStore.clear()
    }

    suspend fun logoutAll(): Result<Unit> = runCatching {
        api.logoutAll()
        tokenStore.clear()
    }
}
