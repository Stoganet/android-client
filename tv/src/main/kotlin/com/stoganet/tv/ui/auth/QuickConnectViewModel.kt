package com.stoganet.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.data.auth.AuthRepository
import com.stoganet.core.data.auth.QuickConnectPollResult
import com.stoganet.core.data.auth.TokenStore
import com.stoganet.tv.StoganetApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class QuickConnectViewModel(private val repository: AuthRepository, private val tokenStore: TokenStore) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val state: StateFlow<QuickConnectUiState> = retries
        .onStart { emit(Unit) }
        .flatMapLatest { attempt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, QuickConnectUiState())

    fun onIntent(intent: QuickConnectIntent) {
        when (intent) {
            QuickConnectIntent.Retry -> retries.tryEmit(Unit)
        }
    }

    private fun attempt(): Flow<QuickConnectUiState> = flow {
        emit(QuickConnectUiState())
        val start = repository.startQuickConnect().getOrElse {
            emit(QuickConnectUiState(status = QuickConnectUiState.Status.Error))
            return@flow
        }
        val waiting = QuickConnectUiState(start.code, QuickConnectUiState.Status.WaitingForApproval)
        emit(waiting)
        emitAll(
            pollResults(start.pollToken).transformWhile { result ->
                result.fold(
                    onSuccess = { poll ->
                        when (poll) {
                            QuickConnectPollResult.Pending -> true

                            is QuickConnectPollResult.Success -> {
                                tokenStore.saveTokens(poll.tokens)
                                false
                            }

                            QuickConnectPollResult.Expired -> {
                                emit(waiting.copy(status = QuickConnectUiState.Status.Expired))
                                false
                            }
                        }
                    },
                    onFailure = {
                        emit(waiting.copy(status = QuickConnectUiState.Status.Error))
                        false
                    },
                )
            },
        )
    }

    private fun pollResults(pollToken: String): Flow<Result<QuickConnectPollResult>> = flow {
        while (true) {
            emit(repository.pollQuickConnect(pollToken))
            delay(POLL_INTERVAL_MS.milliseconds)
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
