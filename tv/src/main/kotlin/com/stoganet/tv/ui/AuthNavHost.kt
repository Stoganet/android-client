package com.stoganet.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stoganet.tv.ui.auth.QuickConnectScreen
import com.stoganet.tv.ui.auth.QuickConnectViewModel

@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "quick-connect") {
        composable("quick-connect") {
            val viewModel: QuickConnectViewModel = viewModel(factory = QuickConnectViewModel.Factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            QuickConnectScreen(state = state, onIntent = viewModel::onIntent)
        }
    }
}
