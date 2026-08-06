package com.stoganet.tv.ui.auth

import androidx.compose.runtime.Immutable

@Immutable
data class LoginUiState(val status: Status = Status.Idle) {
    enum class Status { Idle, Loading, CredentialsError, NetworkError }
}
