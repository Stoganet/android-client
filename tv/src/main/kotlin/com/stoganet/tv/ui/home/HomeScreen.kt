package com.stoganet.tv.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.core.AppRoutes
import com.stoganet.tv.R
import com.stoganet.tv.ui.FocusAfterFirstFrame
import com.stoganet.tv.ui.focusRequesterIf
import com.stoganet.tv.ui.rememberInitialFocusRequester
import kotlinx.collections.immutable.persistentListOf

private const val SEE_MORE_ASPECT_RATIO = 2f / 3f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateTo: (route: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> {
            val loadingFocusRequester = rememberInitialFocusRequester(enabled = true)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .focusRequester(loadingFocusRequester)
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        HomeUiState.Error -> {
            val retryLabel = stringResource(R.string.action_retry)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_error_message),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onIntent(HomeIntent.Retry) },
                        modifier = Modifier.semantics { contentDescription = retryLabel },
                    ) {
                        Text(text = retryLabel)
                    }
                }
            }
        }

        is HomeUiState.Content -> HomeRows(state, onIntent, onNavigateTo, modifier)
    }
}

@Composable
private fun HomeRows(
    state: HomeUiState.Content,
    onIntent: (HomeIntent) -> Unit,
    onNavigateTo: (route: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstNonEmptySectionIndex = state.sections.indexOfFirst { it.items.isNotEmpty() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val clickedItemFocusRequester = remember { FocusRequester() }
    FocusAfterFirstFrame(
        target = if (state.focusedItemKey != null) clickedItemFocusRequester else firstItemFocusRequester,
        enabled = firstNonEmptySectionIndex >= 0,
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        itemsIndexed(state.sections, key = { _, section -> section.id }) { index, section ->
            SectionRow(
                section = section,
                focusedItemKey = state.focusedItemKey,
                clickedItemFocusRequester = clickedItemFocusRequester,
                onSeeMore = section.seeMoreRoute?.let { route -> { onNavigateTo(route) } },
                onItemClick = { key, id ->
                    onIntent(HomeIntent.ItemClicked(key))
                    onNavigateTo(AppRoutes.detail(id))
                },
                firstItemFocusRequester = if (index == firstNonEmptySectionIndex) firstItemFocusRequester else null,
            )
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSectionUiState,
    focusedItemKey: String?,
    clickedItemFocusRequester: FocusRequester,
    onSeeMore: (() -> Unit)?,
    onItemClick: (key: String, id: String) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    Column {
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = if (firstItemFocusRequester != null) {
                Modifier.focusRestorer(fallback = firstItemFocusRequester)
            } else {
                Modifier
            },
        ) {
            itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                val key = "${section.id}/${item.id}"
                PosterCard(
                    posterUrl = item.posterUrl,
                    contentDescription = item.contentDescription,
                    onClick = { onItemClick(key, item.id) },
                    modifier = Modifier
                        .focusRequesterIf(index == 0, firstItemFocusRequester)
                        .focusRequesterIf(key == focusedItemKey, clickedItemFocusRequester),
                )
            }
            if (onSeeMore != null) {
                item(key = "see_more") {
                    SeeMoreCard(onClick = onSeeMore)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeeMoreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_see_more)
    Card(
        onClick = onClick,
        modifier = modifier
            .width(120.dp)
            .aspectRatio(SEE_MORE_ASPECT_RATIO)
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSeeMoreCard() {
    SeeMoreCard(onClick = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewLoading() {
    HomeScreen(state = HomeUiState.Loading, onIntent = {}, onNavigateTo = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewError() {
    HomeScreen(state = HomeUiState.Error, onIntent = {}, onNavigateTo = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewContent() {
    val items = persistentListOf(
        previewItem("1", "Movie One", 2020),
        previewItem("2", "Movie Two", 2021),
        previewItem("3", "Movie Three", 2022),
    )
    HomeScreen(
        state = HomeUiState.Content(
            sections = persistentListOf(
                HomeSectionUiState(
                    "recently_added_movies",
                    R.string.home_section_recently_added_movies,
                    items,
                    hasMore = true,
                ),
                HomeSectionUiState(
                    "all_movies",
                    R.string.home_section_all_movies,
                    items,
                    hasMore = false,
                    seeMoreRoute = AppRoutes.LIBRARY_MOVIES,
                ),
                HomeSectionUiState("all_tv", R.string.home_section_all_tv, items, hasMore = false),
            ),
        ),
        onIntent = {},
        onNavigateTo = {},
    )
}

private fun previewItem(id: String, title: String, year: Int) = HomeItemUiState(
    id = id,
    title = title,
    year = year,
    overview = "",
    posterUrl = "",
    contentDescription = "$title ($year)",
)
