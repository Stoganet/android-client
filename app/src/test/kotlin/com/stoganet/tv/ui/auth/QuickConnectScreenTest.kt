package com.stoganet.tv.ui.auth

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickConnectScreenTest {

    private fun str(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent { QuickConnectScreen(state = QuickConnectUiState(), onIntent = {}) }

        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertIsDisplayed()
        onNodeWithText(str(R.string.login_quick_connect_title)).assertDoesNotExist()
        onNodeWithContentDescription(str(R.string.action_retry)).assertDoesNotExist()
    }

    @Test
    fun waitingState_showsCode() = runComposeUiTest {
        setContent {
            QuickConnectScreen(
                state = QuickConnectUiState(code = "ABC123", status = QuickConnectUiState.Status.WaitingForApproval),
                onIntent = {},
            )
        }

        onNodeWithText("ABC123").assertIsDisplayed()
        onNodeWithText(str(R.string.login_quick_connect_title)).assertIsDisplayed()
    }

    @Test
    fun expiredState_showsRetryButton() = runComposeUiTest {
        setContent {
            QuickConnectScreen(
                state = QuickConnectUiState(status = QuickConnectUiState.Status.Expired),
                onIntent = {},
            )
        }

        onNodeWithText(str(R.string.login_quick_connect_expired)).assertIsDisplayed()
        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun errorState_showsRetryButton() = runComposeUiTest {
        setContent {
            QuickConnectScreen(
                state = QuickConnectUiState(status = QuickConnectUiState.Status.Error),
                onIntent = {},
            )
        }

        onNodeWithText(str(R.string.error_cant_reach_server)).assertIsDisplayed()
        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun retryButton_triggersRetryIntent() = runComposeUiTest {
        var intentFired = false
        setContent {
            QuickConnectScreen(
                state = QuickConnectUiState(status = QuickConnectUiState.Status.Expired),
                onIntent = { if (it == QuickConnectIntent.Retry) intentFired = true },
            )
        }

        onNodeWithContentDescription(str(R.string.action_retry)).requestFocus()
        onNodeWithContentDescription(str(R.string.action_retry)).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(intentFired)
    }
}
