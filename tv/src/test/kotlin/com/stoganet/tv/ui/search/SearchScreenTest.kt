package com.stoganet.tv.ui.search

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.stoganet.tv.R
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
class SearchScreenTest {

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun fakeResults() = SearchUiState.Results(
        query = "movie",
        items = persistentListOf(
            SearchResultUiState("1", "", "Movie One (2020)"),
            SearchResultUiState("2", "", "Movie Two (2021)"),
        ),
    )

    @Test
    fun searchField_requestsFocusOnEntry() = runComposeUiTest {
        setContent {
            SearchScreen(state = SearchUiState.Empty(), onIntent = {}, onNavigateToDetail = {})
        }
        waitForIdle()

        onNodeWithContentDescription(str(R.string.search_hint)).assertIsFocused()
    }

    @Test
    fun emptyState_showsHint() = runComposeUiTest {
        setContent {
            SearchScreen(state = SearchUiState.Empty(), onIntent = {}, onNavigateToDetail = {})
        }

        onNode(hasText(str(R.string.search_empty_hint)) and !hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun typingInField_dispatchesQueryChangedIntent() = runComposeUiTest {
        var received: SearchIntent? = null
        setContent {
            SearchScreen(
                state = SearchUiState.Empty(),
                onIntent = { received = it },
                onNavigateToDetail = {},
            )
        }

        onNodeWithContentDescription(str(R.string.search_hint)).performTextInput("mo")
        waitForIdle()

        assertEquals(SearchIntent.QueryChanged("mo"), received)
    }

    @Test
    fun pressingSearchKey_dispatchesSubmitIntent() = runComposeUiTest {
        var submitted = false
        setContent {
            SearchScreen(
                state = SearchUiState.Empty(),
                onIntent = { if (it == SearchIntent.Submit) submitted = true },
                onNavigateToDetail = {},
            )
        }

        val field = onNodeWithContentDescription(str(R.string.search_hint))
        field.performTextInput("mo")
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(submitted)
    }

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent {
            SearchScreen(state = SearchUiState.Loading("mo"), onIntent = {}, onNavigateToDetail = {})
        }

        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertIsDisplayed()
    }

    @Test
    fun resultsState_showsResultGrid() = runComposeUiTest {
        setContent {
            SearchScreen(state = fakeResults(), onIntent = {}, onNavigateToDetail = {})
        }

        onNodeWithContentDescription("Movie One (2020)").assertIsDisplayed()
        onNodeWithContentDescription("Movie Two (2021)").assertIsDisplayed()
    }

    @Test
    fun resultsState_clickingItem_navigatesToDetail() = runComposeUiTest {
        var navigatedId: String? = null
        setContent {
            SearchScreen(state = fakeResults(), onIntent = {}, onNavigateToDetail = { navigatedId = it })
        }

        onNodeWithContentDescription("Movie One (2020)").requestFocus()
        onNodeWithContentDescription("Movie One (2020)").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("1", navigatedId)
    }

    @Test
    fun noResultsState_showsMessageWithQuery() = runComposeUiTest {
        setContent {
            SearchScreen(state = SearchUiState.NoResults("zzz"), onIntent = {}, onNavigateToDetail = {})
        }

        onNodeWithText(str(R.string.search_no_results, "zzz")).assertIsDisplayed()
    }

    @Test
    fun errorState_showsRetryButton() = runComposeUiTest {
        setContent {
            SearchScreen(state = SearchUiState.Error("movie"), onIntent = {}, onNavigateToDetail = {})
        }

        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun errorState_retryButton_dispatchesSubmitIntent() = runComposeUiTest {
        var submitted = false
        setContent {
            SearchScreen(
                state = SearchUiState.Error("movie"),
                onIntent = { if (it == SearchIntent.Submit) submitted = true },
                onNavigateToDetail = {},
            )
        }

        val retry = onNodeWithContentDescription(str(R.string.action_retry))
        retry.requestFocus()
        retry.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(submitted)
    }
}
