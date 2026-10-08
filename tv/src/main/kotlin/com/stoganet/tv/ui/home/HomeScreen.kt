package com.stoganet.tv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.core.AppRoutes
import com.stoganet.tv.R
import com.stoganet.tv.ui.FocusAfterFirstFrame
import com.stoganet.tv.ui.focusRequesterIf
import com.stoganet.tv.ui.rememberInitialFocusRequester
import kotlinx.collections.immutable.persistentListOf

private const val HERO_WEIGHT = 0.55f

// Rows scroll only by pinning the focused row to the top, not by default focus scrolling
private val NoBringIntoViewScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

private data class FocusedItem(val sectionIndex: Int, val item: HomeItemUiState)

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
    var focused by remember { mutableStateOf(state.initialFocus()) }
    Box(modifier = modifier.fillMaxSize()) {
        HomeHeroBackdrop(backdropUrl = focused?.item?.backdropUrl)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(HERO_WEIGHT)
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                focused?.let { HomeHeroText(item = it.item) }
            }
            PinnedSectionRows(
                state = state,
                focusedSectionIndex = focused?.sectionIndex ?: 0,
                onIntent = onIntent,
                onNavigateTo = onNavigateTo,
                onItemFocus = { sectionIndex, item -> focused = FocusedItem(sectionIndex, item) },
                modifier = Modifier.weight(1f - HERO_WEIGHT),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedSectionRows(
    state: HomeUiState.Content,
    focusedSectionIndex: Int,
    onIntent: (HomeIntent) -> Unit,
    onNavigateTo: (route: String) -> Unit,
    onItemFocus: (sectionIndex: Int, item: HomeItemUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowBringIntoViewSpec = LocalBringIntoViewSpec.current

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusedSectionIndex)
    var viewportHeight by remember { mutableStateOf(0.dp) }

    val firstNonEmptySectionIndex = state.sections.indexOfFirst { it.items.isNotEmpty() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val clickedItemFocusRequester = remember { FocusRequester() }

    FocusAfterFirstFrame(
        target = if (state.focusedItemKey != null) clickedItemFocusRequester else firstItemFocusRequester,
        enabled = firstNonEmptySectionIndex >= 0,
    )
    LaunchedEffect(focusedSectionIndex) { listState.animateScrollToItem(focusedSectionIndex) }

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoViewScroll) {
        LazyColumn(
            state = listState,
            // Lets the last rows scroll up to the pinned position too
            contentPadding = PaddingValues(bottom = viewportHeight),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeight = with(density) { it.height.toDp() } },
        ) {
            itemsIndexed(state.sections, key = { _, section -> section.id }) { index, section ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringIntoViewSpec) {
                    SectionRow(
                        section = section,
                        focusedItemKey = state.focusedItemKey,
                        clickedItemFocusRequester = clickedItemFocusRequester,
                        onSeeMore = section.seeMoreRoute?.let { route -> { onNavigateTo(route) } },
                        onItemClick = { key, id ->
                            onIntent(HomeIntent.ItemClicked(key))
                            onNavigateTo(AppRoutes.detail(id))
                        },
                        onItemFocus = { item -> onItemFocus(index, item) },
                        firstItemFocusRequester = if (index == firstNonEmptySectionIndex) {
                            firstItemFocusRequester
                        } else {
                            null
                        },
                    )
                }
            }
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
    onItemFocus: (HomeItemUiState) -> Unit,
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
                BackdropCard(
                    title = item.title,
                    imageUrl = item.thumbUrl,
                    contentDescription = item.contentDescription,
                    onClick = { onItemClick(key, item.id) },
                    modifier = Modifier
                        .focusRequesterIf(index == 0, firstItemFocusRequester)
                        .focusRequesterIf(key == focusedItemKey, clickedItemFocusRequester)
                        .onFocusChanged { if (it.isFocused) onItemFocus(item) },
                    imageHasTitle = item.thumbHasLogo,
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

@Composable
private fun SeeMoreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_see_more)
    Card(
        onClick = onClick,
        modifier = modifier
            .width(BACKDROP_CARD_WIDTH)
            .aspectRatio(BACKDROP_CARD_ASPECT_RATIO)
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

private fun HomeUiState.Content.initialFocus(): FocusedItem? {
    sections.forEachIndexed { index, section ->
        section.items.firstOrNull { "${section.id}/${it.id}" == focusedItemKey }
            ?.let { return FocusedItem(index, it) }
    }
    val index = sections.indexOfFirst { it.items.isNotEmpty() }
    return if (index >= 0) FocusedItem(index, sections[index].items.first()) else null
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
    contentDescription = "$title ($year)",
)
