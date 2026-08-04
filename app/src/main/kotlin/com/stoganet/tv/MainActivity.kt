package com.stoganet.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.stoganet.tv.data.auth.TokenStore
import com.stoganet.tv.ui.AppNavHost
import com.stoganet.tv.ui.AuthNavHost
import com.stoganet.tv.ui.theme.StoganetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val services = (application as StoganetApp).services
        setContent {
            App(services.tokenStore)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun App(tokenStore: TokenStore, modifier: Modifier = Modifier) {
    val isAuthenticated by tokenStore.isAuthenticated.collectAsStateWithLifecycle(false)
    StoganetTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            if (isAuthenticated) AppNavHost() else AuthNavHost()
        }
    }
}
