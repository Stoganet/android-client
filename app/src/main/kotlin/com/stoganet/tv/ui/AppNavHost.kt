package com.stoganet.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.stoganet.tv.R
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.ui.detail.DetailScreen
import com.stoganet.tv.ui.detail.DetailViewModel
import com.stoganet.tv.ui.home.HomeScreen
import com.stoganet.tv.ui.home.HomeViewModel
import com.stoganet.tv.ui.library.LibraryScreen
import com.stoganet.tv.ui.library.LibraryViewModel
import com.stoganet.tv.ui.player.PlayerScreen
import com.stoganet.tv.ui.player.PlayerViewModel

@Suppress("LongMethod")
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigateTo: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(AppRoutes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavHost(navController = navController, startDestination = AppRoutes.HOME) {
        composable(AppRoutes.HOME) {
            DrawerScaffold(currentRoute = currentRoute, navigateTo = navigateTo) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onIntent = vm::onIntent,
                    onNavigateTo = { route -> navController.navigate(route) },
                )
            }
        }
        composable(AppRoutes.LIBRARY_MOVIES) {
            DrawerScaffold(currentRoute = currentRoute, navigateTo = navigateTo) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(MediaType.MOVIE))
                val state by vm.state.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = state,
                    onIntent = vm::onIntent,
                    onNavigateTo = { route -> navController.navigate(route) },
                )
            }
        }
        composable(AppRoutes.LIBRARY_TV) {
            DrawerScaffold(currentRoute = currentRoute, navigateTo = navigateTo) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(MediaType.TV))
                val state by vm.state.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = state,
                    onIntent = vm::onIntent,
                    onNavigateTo = { route -> navController.navigate(route) },
                )
            }
        }
        composable(AppRoutes.DETAIL) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(id))
            val state by vm.state.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onIntent = vm::onIntent,
                onNavigateToPlayer = { streamUrl, positionMs ->
                    navController.navigate(AppRoutes.player(id, streamUrl, positionMs))
                },
            )
        }
        composable(
            route = AppRoutes.PLAYER,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("streamUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("positionMs") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")
            val positionMs = backStackEntry.arguments?.getLong("positionMs") ?: 0L
            val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(id, streamUrl, positionMs))
            val state by vm.state.collectAsStateWithLifecycle()
            PlayerScreen(
                state = state,
                onBack = {
                    vm.player.stop()
                    navController.popBackStack()
                },
                player = vm.player,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerScaffold(currentRoute: String?, navigateTo: (String) -> Unit, content: @Composable () -> Unit) {
    NavigationDrawer(
        drawerContent = { NavDrawerContent(currentRoute = currentRoute, navigateTo = navigateTo) },
    ) {
        content()
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationDrawerScope.NavDrawerContent(currentRoute: String?, navigateTo: (String) -> Unit) {
    val homeLabel = stringResource(R.string.nav_home)
    val moviesLabel = stringResource(R.string.nav_movies)
    val tvLabel = stringResource(R.string.nav_tv_shows)
    val homeDesc = stringResource(R.string.nav_item_selected_description, homeLabel)
    val moviesDesc = stringResource(R.string.nav_item_selected_description, moviesLabel)
    val tvDesc = stringResource(R.string.nav_item_selected_description, tvLabel)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .selectableGroup(),
    ) {
        NavigationDrawerItem(
            selected = currentRoute == AppRoutes.HOME,
            onClick = { navigateTo(AppRoutes.HOME) },
            leadingContent = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
            modifier = Modifier.semantics {
                contentDescription = if (currentRoute == AppRoutes.HOME) homeDesc else homeLabel
            },
        ) { Text(homeLabel) }
        NavigationDrawerItem(
            selected = currentRoute == AppRoutes.LIBRARY_MOVIES,
            onClick = { navigateTo(AppRoutes.LIBRARY_MOVIES) },
            leadingContent = { Icon(painterResource(R.drawable.ic_movie), contentDescription = null) },
            modifier = Modifier.semantics {
                contentDescription = if (currentRoute == AppRoutes.LIBRARY_MOVIES) moviesDesc else moviesLabel
            },
        ) { Text(moviesLabel) }
        NavigationDrawerItem(
            selected = currentRoute == AppRoutes.LIBRARY_TV,
            onClick = { navigateTo(AppRoutes.LIBRARY_TV) },
            leadingContent = { Icon(painterResource(R.drawable.ic_tv), contentDescription = null) },
            modifier = Modifier.semantics {
                contentDescription = if (currentRoute == AppRoutes.LIBRARY_TV) tvDesc else tvLabel
            },
        ) { Text(tvLabel) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 300, heightDp = 720)
@Composable
private fun PreviewNavDrawerHomeSelected() {
    NavigationDrawer(drawerContent = { NavDrawerContent(currentRoute = AppRoutes.HOME, navigateTo = {}) }) {}
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 300, heightDp = 720)
@Composable
private fun PreviewNavDrawerMoviesSelected() {
    NavigationDrawer(
        drawerContent = { NavDrawerContent(currentRoute = AppRoutes.LIBRARY_MOVIES, navigateTo = {}) },
    ) {}
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 300, heightDp = 720)
@Composable
private fun PreviewNavDrawerTvSelected() {
    NavigationDrawer(
        drawerContent = { NavDrawerContent(currentRoute = AppRoutes.LIBRARY_TV, navigateTo = {}) },
    ) {}
}
