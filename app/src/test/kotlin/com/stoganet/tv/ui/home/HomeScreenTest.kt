package com.stoganet.tv.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.stoganet.tv.R
import com.stoganet.tv.ui.AppRoutes
import kotlinx.collections.immutable.persistentListOf
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

    private fun stubItems() = persistentListOf(
        HomeItemUiState("1", "", "Movie One (2020)"),
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
                            items = persistentListOf(HomeItemUiState("item-1", "", "Movie One (2020)")),
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
}
