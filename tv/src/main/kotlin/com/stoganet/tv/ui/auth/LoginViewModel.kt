package com.stoganet.tv.ui.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.data.auth.AuthRepository
import com.stoganet.core.data.auth.LoginResult
import com.stoganet.core.data.auth.TokenStore
import com.stoganet.tv.StoganetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository, private val tokenStore: TokenStore) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.Submit -> submit(intent.username, intent.password)
        }
    }

    private fun submit(username: String, password: String) {
        if (username.isEmpty() || password.isEmpty()) return

        _state.update { it.copy(status = LoginUiState.Status.Loading) }
        viewModelScope.launch {
            repository.login(username, password, Build.MODEL)
                .onSuccess { result ->
                    when (result) {
                        is LoginResult.Success -> tokenStore.saveTokens(result.tokens)

                        LoginResult.InvalidCredentials ->
                            _state.update { it.copy(status = LoginUiState.Status.CredentialsError) }

                        LoginResult.NetworkError ->
                            _state.update { it.copy(status = LoginUiState.Status.NetworkError) }
                    }
                }
                .onFailure {
                    _state.update { it.copy(status = LoginUiState.Status.NetworkError) }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                LoginViewModel(app.services.authRepository, app.services.tokenStore)
            }
        }
    }
}
