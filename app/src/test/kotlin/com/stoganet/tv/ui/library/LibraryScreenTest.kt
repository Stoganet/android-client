package com.stoganet.tv.ui.library

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
class LibraryScreenTest {

    private fun str(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent { LibraryScreen(state = LibraryUiState.Loading, onIntent = {}, onNavigateTo = {}) }

        onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() = runComposeUiTest {
        setContent { LibraryScreen(state = LibraryUiState.Error, onIntent = {}, onNavigateTo = {}) }

        onNodeWithText(str(R.string.library_error_message)).assertIsDisplayed()
    }

    @Test
    fun errorState_showsRetryButton() = runComposeUiTest {
        setContent { LibraryScreen(state = LibraryUiState.Error, onIntent = {}, onNavigateTo = {}) }

        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun retryButton_triggersRetryIntent() = runComposeUiTest {
        var intentFired = false
        setContent {
            LibraryScreen(
                state = LibraryUiState.Error,
                onIntent = { if (it == LibraryIntent.Retry) intentFired = true },
                onNavigateTo = {},
            )
        }

        onNodeWithContentDescription(str(R.string.action_retry)).requestFocus()
        onNodeWithContentDescription(str(R.string.action_retry)).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(intentFired)
    }

    @Test
    fun contentState_showsItemByContentDescription() = runComposeUiTest {
        setContent {
            LibraryScreen(
                state = LibraryUiState.Content(
                    items = persistentListOf(
                        LibraryItemUiState(
                            id = "1",
                            posterUrl = "",
                            contentDescription = "Inception (2010)",
                        ),
                    ),
                    hasMore = false,
                    isLoadingMore = false,
                ),
                onIntent = {},
                onNavigateTo = {},
            )
        }

        onNodeWithContentDescription("Inception (2010)").assertIsDisplayed()
    }

    @Test
    fun contentState_loadMoreError_showsErrorMessage() = runComposeUiTest {
        setContent {
            LibraryScreen(
                state = LibraryUiState.Content(
                    items = persistentListOf(
                        LibraryItemUiState(id = "1", posterUrl = "", contentDescription = "Inception (2010)"),
                    ),
                    hasMore = true,
                    isLoadingMore = false,
                    hasLoadMoreError = true,
                ),
                onIntent = {},
                onNavigateTo = {},
            )
        }

        onNodeWithText(str(R.string.library_load_more_error)).assertIsDisplayed()
    }

    @Test
    fun contentState_posterCard_tapFiresOnNavigateTo() = runComposeUiTest {
        var navigatedRoute: String? = null
        setContent {
            LibraryScreen(
                state = LibraryUiState.Content(
                    items = persistentListOf(
                        LibraryItemUiState(id = "item-1", posterUrl = "", contentDescription = "Inception (2010)"),
                    ),
                    hasMore = false,
                    isLoadingMore = false,
                ),
                onIntent = {},
                onNavigateTo = { route -> navigatedRoute = route },
            )
        }

        onNodeWithContentDescription("Inception (2010)").requestFocus()
        onNodeWithContentDescription("Inception (2010)").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(AppRoutes.detail("item-1"), navigatedRoute)
    }
}
