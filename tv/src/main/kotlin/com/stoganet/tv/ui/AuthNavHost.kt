package com.stoganet.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stoganet.tv.ui.auth.LoginScreen
import com.stoganet.tv.ui.auth.LoginViewModel
import com.stoganet.tv.ui.auth.QuickConnectScreen
import com.stoganet.tv.ui.auth.QuickConnectViewModel

private const val ROUTE_LOGIN = "login"
private const val ROUTE_QUICK_CONNECT = "quick-connect"

@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
        composable(ROUTE_LOGIN) {
            val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            LoginScreen(
                state = state,
                onIntent = viewModel::onIntent,
                onSwitchToQuickConnect = { navController.navigate(ROUTE_QUICK_CONNECT) },
            )
        }
        composable(ROUTE_QUICK_CONNECT) {
            val viewModel: QuickConnectViewModel = viewModel(factory = QuickConnectViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            QuickConnectScreen(
                state = state,
                onIntent = viewModel::onIntent,
                onSwitchToPassword = { navController.navigate(ROUTE_LOGIN) },
            )
        }
    }
}
