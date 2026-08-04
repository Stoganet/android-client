package com.stoganet.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R
import com.stoganet.tv.ui.AppRoutes
import com.stoganet.tv.ui.home.PosterCard
import kotlinx.collections.immutable.persistentListOf

private const val GRID_COLUMNS = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onIntent: (LibraryIntent) -> Unit,
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LibraryUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        LibraryUiState.Error -> {
            val retryLabel = stringResource(R.string.action_retry)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_error_message),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onIntent(LibraryIntent.Retry) },
                        modifier = Modifier.semantics { contentDescription = retryLabel },
                    ) {
                        Text(text = retryLabel)
                    }
                }
            }
        }

        is LibraryUiState.Content -> LibraryGrid(
            state = state,
            onIntent = onIntent,
            onNavigateTo = onNavigateTo,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState.Content,
    onIntent: (LibraryIntent) -> Unit,
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(state.items.size) {
        derivedStateOf {
            val info = gridState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) return@derivedStateOf false
            info.last().index >= state.items.size - GRID_COLUMNS * 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) currentOnIntent(LibraryIntent.LoadMore)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.id }) { item ->
            PosterCard(
                posterUrl = item.posterUrl,
                contentDescription = item.contentDescription,
                onClick = { onNavigateTo(AppRoutes.detail(item.id)) },
            )
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.hasLoadMoreError) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_load_more_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewLoading() {
    LibraryScreen(state = LibraryUiState.Loading, onIntent = {}, onNavigateTo = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewError() {
    LibraryScreen(state = LibraryUiState.Error, onIntent = {}, onNavigateTo = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewContent() {
    val items = persistentListOf(
        LibraryItemUiState("1", "", "Movie One (2020)"),
        LibraryItemUiState("2", "", "Movie Two (2021)"),
        LibraryItemUiState("3", "", "Movie Three (2022)"),
        LibraryItemUiState("4", "", "Movie Four (2023)"),
        LibraryItemUiState("5", "", "Movie Five (2024)"),
        LibraryItemUiState("6", "", "Movie Six (2025)"),
    )
    LibraryScreen(
        state = LibraryUiState.Content(items = items, hasMore = false, isLoadingMore = false),
        onIntent = {},
        onNavigateTo = {},
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewContentLoadingMore() {
    val items = persistentListOf(
        LibraryItemUiState("1", "", "Movie One (2020)"),
        LibraryItemUiState("2", "", "Movie Two (2021)"),
    )
    LibraryScreen(
        state = LibraryUiState.Content(items = items, hasMore = true, isLoadingMore = true),
        onIntent = {},
        onNavigateTo = {},
    )
}
