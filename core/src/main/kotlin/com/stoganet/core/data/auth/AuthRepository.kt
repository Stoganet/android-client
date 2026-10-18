package com.stoganet.core.data.auth

import com.stoganet.core.api.model.QuickConnectStartResponse
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure
import com.stoganet.core.util.suspendRunCatching
import kotlinx.coroutines.flow.Flow

private const val TAG = "AuthRepository"

class AuthRepository(private val api: StoganetApi, private val tokenStore: TokenStore) {

    val isSignedIn: Flow<Boolean> = tokenStore.isAuthenticated

    suspend fun login(username: String, password: String, deviceLabel: String?): Result<LoginResult> =
        suspendRunCatching {
            api.login(username, password, deviceLabel)
        }.logOnFailure(TAG)

    suspend fun startQuickConnect(): Result<QuickConnectStartResponse> = suspendRunCatching {
        api.startQuickConnect()
    }.logOnFailure(TAG)

    suspend fun pollQuickConnect(pollToken: String): Result<QuickConnectPollResult> = suspendRunCatching {
        api.pollQuickConnect(pollToken)
    }.logOnFailure(TAG)

    suspend fun logout(refreshToken: String): Result<Unit> = suspendRunCatching {
        api.logout(refreshToken)
        tokenStore.clear()
    }.logOnFailure(TAG)

    suspend fun logoutAll(): Result<Unit> = suspendRunCatching {
        api.logoutAll()
        tokenStore.clear()
    }.logOnFailure(TAG)
}
