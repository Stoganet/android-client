package com.stoganet.tv.ui.library

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test
    fun contentState_withFocusedItemId_focusesThatItem() = runComposeUiTest {
        setContent {
            LibraryScreen(state = gridState().copy(focusedItemId = "item-9"), onIntent = {}, onNavigateTo = {})
        }
        waitForIdle()

        onNodeWithContentDescription("Movie 9").assertIsFocused()
    }

    @Test
    fun contentState_withoutFocusedItemId_focusesFirstItem() = runComposeUiTest {
        setContent { LibraryScreen(state = gridState(), onIntent = {}, onNavigateTo = {}) }
        waitForIdle()

        onNodeWithContentDescription("Movie 1").assertIsFocused()
    }

    @Test
    fun posterCard_click_firesItemClicked() = runComposeUiTest {
        val intents = mutableListOf<LibraryIntent>()
        setContent { LibraryScreen(state = gridState(), onIntent = { intents += it }, onNavigateTo = {}) }

        onNodeWithContentDescription("Movie 4").requestFocus()
        onNodeWithContentDescription("Movie 4").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(LibraryIntent.ItemClicked("item-4") in intents)
    }

    private fun gridState() = LibraryUiState.Content(
        items = (1..60).map { LibraryItemUiState("item-$it", "", "Movie $it") }.toPersistentList(),
        hasMore = false,
        isLoadingMore = false,
    )

    @Test
    fun returningToLibrary_focusesClickedItem() = runComposeUiTest {
        var state by mutableStateOf(gridState())
        var showLibrary by mutableStateOf(true)
        setContent {
            val holder = rememberSaveableStateHolder()
            if (showLibrary) {
                holder.SaveableStateProvider("library") {
                    LibraryScreen(
                        state = state,
                        onIntent = { if (it is LibraryIntent.ItemClicked) state = state.copy(focusedItemId = it.id) },
                        onNavigateTo = { showLibrary = false },
                    )
                }
            }
        }

        onNodeWithContentDescription("Movie 1").requestFocus()
        onRoot().performKeyInput {
            repeat(3) { pressKey(Key.DirectionDown) }
            repeat(2) { pressKey(Key.DirectionRight) }
        }
        waitForIdle()
        onNodeWithContentDescription("Movie 21").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        showLibrary = true
        waitForIdle()

        onNodeWithContentDescription("Movie 21").assertIsFocused()
    }
}
