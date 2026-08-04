package com.stoganet.tv.ui.auth

import androidx.compose.runtime.Immutable

@Immutable
data class QuickConnectUiState(val code: String = "", val status: Status = Status.Loading) {
    enum class Status { Loading, WaitingForApproval, Expired, Error }
}
