package com.stoganet.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import com.stoganet.tv.ui.AppNavHost
import com.stoganet.tv.ui.AuthNavHost
import com.stoganet.tv.ui.theme.StoganetTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels { MainActivityViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.state.value == MainActivityUiState.Loading }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            App(state)
        }
    }
}

@Composable
fun App(state: MainActivityUiState, modifier: Modifier = Modifier) {
    StoganetTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            when (state) {
                MainActivityUiState.Loading -> Unit
                MainActivityUiState.SignedIn -> AppNavHost()
                MainActivityUiState.SignedOut -> AuthNavHost()
            }
        }
    }
}
