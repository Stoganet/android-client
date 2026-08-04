package com.stoganet.tv.ui.player

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.stoganet.tv.R
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerScreenTest {

    private val mockPlayer = mockk<ExoPlayer>(relaxed = true)

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent {
            PlayerScreen(state = PlayerUiState.Loading, onBack = {}, player = mockPlayer)
        }

        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() = runComposeUiTest {
        setContent {
            PlayerScreen(state = PlayerUiState.Error, onBack = {}, player = mockPlayer)
        }

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        onNodeWithText(ctx.getString(R.string.player_error_message)).assertIsDisplayed()
    }

    @Test
    fun errorState_showsBackButton() = runComposeUiTest {
        setContent {
            PlayerScreen(state = PlayerUiState.Error, onBack = {}, player = mockPlayer)
        }

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        onNodeWithText(ctx.getString(R.string.player_back_content_description)).assertIsDisplayed()
    }

    @Test
    fun errorState_backButton_invokesCallback() = runComposeUiTest {
        var backCalled = false
        setContent {
            PlayerScreen(state = PlayerUiState.Error, onBack = { backCalled = true }, player = mockPlayer)
        }

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val backDesc = ctx.getString(R.string.player_back_content_description)
        onNodeWithText(backDesc).requestFocus()
        onNodeWithText(backDesc).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(backCalled)
    }
}
