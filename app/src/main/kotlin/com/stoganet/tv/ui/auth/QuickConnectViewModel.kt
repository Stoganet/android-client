package com.stoganet.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.tv.StoganetApp
import com.stoganet.tv.data.auth.AuthRepository
import com.stoganet.tv.data.auth.QuickConnectPollResult
import com.stoganet.tv.data.auth.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class QuickConnectViewModel(private val repository: AuthRepository, private val tokenStore: TokenStore) : ViewModel() {

    private val _state = MutableStateFlow(QuickConnectUiState())
    val state: StateFlow<QuickConnectUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        startAuth()
    }

    fun onIntent(intent: QuickConnectIntent) {
        when (intent) {
            QuickConnectIntent.Retry -> {
                pollJob?.cancel()
                startAuth()
            }
        }
    }

    private fun startAuth() {
        _state.update { it.copy(code = "", status = QuickConnectUiState.Status.Loading) }
        viewModelScope.launch {
            repository.startQuickConnect()
                .onSuccess { start ->
                    _state.update { it.copy(code = start.code, status = QuickConnectUiState.Status.WaitingForApproval) }
                    pollJob = launch { pollLoop(start.pollToken) }
                }
                .onFailure {
                    _state.update { it.copy(status = QuickConnectUiState.Status.Error) }
                }
        }
    }

    private suspend fun pollLoop(pollToken: String) {
        while (true) {
            when (
                val result = repository.pollQuickConnect(pollToken).getOrElse {
                    _state.update { it.copy(status = QuickConnectUiState.Status.Error) }
                    return
                }
            ) {
                QuickConnectPollResult.Pending -> delay(POLL_INTERVAL_MS.milliseconds)

                is QuickConnectPollResult.Success -> {
                    tokenStore.saveTokens(result.tokens)
                    return
                }

                QuickConnectPollResult.Expired -> {
                    _state.update { it.copy(status = QuickConnectUiState.Status.Expired) }
                    return
                }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                QuickConnectViewModel(app.services.authRepository, app.services.tokenStore)
            }
        }
    }
}
