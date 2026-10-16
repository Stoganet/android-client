package com.stoganet.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stoganet.core.data.auth.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainActivityViewModel(repository: AuthRepository) : ViewModel() {

    val state: StateFlow<MainActivityUiState> = repository.isSignedIn
        .map { signedIn -> if (signedIn) MainActivityUiState.SignedIn else MainActivityUiState.SignedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainActivityUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StoganetApp
                MainActivityViewModel(app.services.authRepository)
            }
        }
    }
}
