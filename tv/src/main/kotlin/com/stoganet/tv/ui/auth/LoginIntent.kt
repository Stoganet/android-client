package com.stoganet.tv.ui.auth

sealed interface LoginIntent {
    data class Submit(val username: String, val password: String) : LoginIntent
}
