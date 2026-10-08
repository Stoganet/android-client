package com.stoganet.tv.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.stoganet.core.AppRoutes
import com.stoganet.tv.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {

    private fun str(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun item(id: String, description: String) = HomeItemUiState(
        id = id,
        title = description,
        year = 2020,
        overview = "",
        contentDescription = description,
    )

    private fun stubItems() = persistentListOf(
        item("1", "Movie One (2020)"),
    )

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent { HomeScreen(state = HomeUiState.Loading, onIntent = {}, onNavigateTo = {}) }

        onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() = runComposeUiTest {
        setContent { HomeScreen(state = HomeUiState.Error, onIntent = {}, onNavigateTo = {}) }

        onNodeWithText(str(R.string.home_error_message)).assertIsDisplayed()
    }

    @Test
    fun errorState_showsRetryButton() = runComposeUiTest {
        setContent { HomeScreen(state = HomeUiState.Error, onIntent = {}, onNavigateTo = {}) }

        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun retryButton_triggersRetryIntent() = runComposeUiTest {
        var intentFired = false
        setContent {
            HomeScreen(
                state = HomeUiState.Error,
                onIntent = { if (it == HomeIntent.Retry) intentFired = true },
                onNavigateTo = {},
            )
        }

        onNodeWithContentDescription(str(R.string.action_retry)).requestFocus()
        onNodeWithContentDescription(str(R.string.action_retry)).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(intentFired)
    }

    @Test
    fun contentState_showsSectionTitle() = runComposeUiTest {
        setContent {
            HomeScreen(
                state = HomeUiState.Content(
                    sections = persistentListOf(
                        HomeSectionUiState(
                            id = "all_movies",
                            titleRes = R.string.home_section_all_movies,
                            items = stubItems(),
                            hasMore = false,
                            seeMoreRoute = null,
                        ),
                    ),
                ),
                onIntent = {},
                onNavigateTo = {},
            )
        }

        onNodeWithText(str(R.string.home_section_all_movies)).assertIsDisplayed()
    }

    @Test
    fun contentState_seeMoreCard_visibleWhenRouteSet() = runComposeUiTest {
        setContent {
            HomeScreen(
                state = HomeUiState.Content(
                    sections = persistentListOf(
                        HomeSectionUiState(
                            id = "all_movies",
                            titleRes = R.string.home_section_all_movies,
                            items = stubItems(),
                            hasMore = false,
                            seeMoreRoute = "library/movies",
                        ),
                    ),
                ),
                onIntent = {},
                onNavigateTo = {},
            )
        }

        onNodeWithContentDescription(str(R.string.home_see_more)).assertIsDisplayed()
    }

    @Test
    fun contentState_seeMoreCard_notVisibleWhenRouteNull() = runComposeUiTest {
        setContent {
            HomeScreen(
                state = HomeUiState.Content(
                    sections = persistentListOf(
                        HomeSectionUiState(
                            id = "recently_added_movies",
                            titleRes = R.string.home_section_recently_added_movies,
                            items = stubItems(),
                            hasMore = false,
                            seeMoreRoute = null,
                        ),
                    ),
                ),
                onIntent = {},
                onNavigateTo = {},
            )
        }

        onNodeWithContentDescription(str(R.string.home_see_more)).assertDoesNotExist()
    }

    @Test
    fun seeMoreCard_tapFiresOnNavigateTo() = runComposeUiTest {
        var navigatedRoute: String? = null
        setContent {
            HomeScreen(
                state = HomeUiState.Content(
                    sections = persistentListOf(
                        HomeSectionUiState(
                            id = "all_movies",
                            titleRes = R.string.home_section_all_movies,
                            items = stubItems(),
                            hasMore = false,
                            seeMoreRoute = "library/movies",
                        ),
                    ),
                ),
                onIntent = {},
                onNavigateTo = { route -> navigatedRoute = route },
            )
        }

        onNodeWithContentDescription(str(R.string.home_see_more)).requestFocus()
        onNodeWithContentDescription(str(R.string.home_see_more)).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("library/movies", navigatedRoute)
    }

    @Test
    fun contentState_posterCard_tapFiresOnNavigateTo() = runComposeUiTest {
        var navigatedRoute: String? = null
        setContent {
            HomeScreen(
                state = HomeUiState.Content(
                    sections = persistentListOf(
                        HomeSectionUiState(
                            id = "all_movies",
                            titleRes = R.string.home_section_all_movies,
                            items = persistentListOf(item("item-1", "Movie One (2020)")),
                            hasMore = false,
                            seeMoreRoute = null,
                        ),
                    ),
                ),
                onIntent = {},
                onNavigateTo = { route -> navigatedRoute = route },
            )
        }

        onNodeWithContentDescription("Movie One (2020)").requestFocus()
        onNodeWithContentDescription("Movie One (2020)").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(AppRoutes.detail("item-1"), navigatedRoute)
    }

    @Test
    fun returningToHome_focusesClickedItemInFirstRow() = runComposeUiTest {
        assertFocusRestoredAfterReturn(rowPrefix = "Recent")
    }

    @Test
    fun returningToHome_focusesClickedItemInLaterRow() = runComposeUiTest {
        assertFocusRestoredAfterReturn(rowPrefix = "Movie")
    }

    @Test
    fun contentState_withFocusedItemKey_focusesThatItem() = runComposeUiTest {
        setContent {
            HomeScreen(
                state = twoRowState().copy(focusedItemKey = "movies/Movie-2"),
                onIntent = {},
                onNavigateTo = {},
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Movie 2").assertIsFocused()
    }

    @Test
    fun contentState_withoutFocusedItemKey_focusesFirstItem() = runComposeUiTest {
        setContent { HomeScreen(state = twoRowState(), onIntent = {}, onNavigateTo = {}) }
        waitForIdle()

        onNodeWithContentDescription("Recent 1").assertIsFocused()
    }

    @Test
    fun posterCard_click_firesItemClickedWithSectionAndItemId() = runComposeUiTest {
        var intent: HomeIntent? = null
        setContent { HomeScreen(state = twoRowState(), onIntent = { intent = it }, onNavigateTo = {}) }

        onNodeWithContentDescription("Movie 2").requestFocus()
        onNodeWithContentDescription("Movie 2").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(HomeIntent.ItemClicked("movies/Movie-2"), intent)
    }

    @Test
    fun emptyFirstRow_focusesFirstItemOfFirstNonEmptyRow() = runComposeUiTest {
        setContent { HomeScreen(state = emptyFirstRowState(), onIntent = {}, onNavigateTo = {}) }
        waitForIdle()

        onNodeWithContentDescription("Show 1").assertIsFocused()
    }

    @Test
    fun emptyFirstRow_returningToHome_focusesClickedItem() = runComposeUiTest {
        var state by mutableStateOf(emptyFirstRowState())
        var showHome by mutableStateOf(true)
        setContent {
            val holder = rememberSaveableStateHolder()
            if (showHome) {
                holder.SaveableStateProvider("home") {
                    HomeScreen(
                        state = state,
                        onIntent = { if (it is HomeIntent.ItemClicked) state = state.copy(focusedItemKey = it.key) },
                        onNavigateTo = { showHome = false },
                    )
                }
            }
        }

        onNodeWithContentDescription("Show 1").requestFocus()
        onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionRight) } }
        waitForIdle()
        onNodeWithContentDescription("Show 4").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        showHome = true
        waitForIdle()

        onNodeWithContentDescription("Show 4").assertIsFocused()
    }

    private fun emptyFirstRowState() = HomeUiState.Content(
        sections = persistentListOf(
            HomeSectionUiState("recent_movies", R.string.home_section_recently_added_movies, persistentListOf(), false),
            HomeSectionUiState(
                "recent_tv",
                R.string.home_section_recently_added_tv,
                (1..10).map { item("Show-$it", "Show $it") }.toPersistentList(),
                false,
            ),
        ),
    )

    private fun twoRowState(): HomeUiState.Content {
        fun items(prefix: String) = (1..10).map { item("$prefix-$it", "$prefix $it") }.toPersistentList()
        return HomeUiState.Content(
            sections = persistentListOf(
                HomeSectionUiState("recent", R.string.home_section_recently_added_movies, items("Recent"), false),
                HomeSectionUiState("movies", R.string.home_section_all_movies, items("Movie"), false),
            ),
        )
    }

    private fun ComposeUiTest.assertFocusRestoredAfterReturn(rowPrefix: String) {
        var state by mutableStateOf(twoRowState())
        var showHome by mutableStateOf(true)
        setContent {
            val holder = rememberSaveableStateHolder()
            if (showHome) {
                holder.SaveableStateProvider("home") {
                    HomeScreen(
                        state = state,
                        onIntent = { if (it is HomeIntent.ItemClicked) state = state.copy(focusedItemKey = it.key) },
                        onNavigateTo = { showHome = false },
                    )
                }
            }
        }

        onNodeWithContentDescription("$rowPrefix 1").requestFocus()
        onRoot().performKeyInput { repeat(5) { pressKey(Key.DirectionRight) } }
        waitForIdle()
        onNodeWithContentDescription("$rowPrefix 6").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        showHome = true
        waitForIdle()

        onNodeWithContentDescription("$rowPrefix 6").assertIsFocused()
    }
}
