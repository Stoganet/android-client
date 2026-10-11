package com.stoganet.tv.ui.player

import android.content.Context
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.stoganet.tv.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerScreenTest {

    private val mockPlayer = mockk<ExoPlayer>(relaxed = true) {
        every { applicationLooper } returns android.os.Looper.getMainLooper()
    }

    private val intents = mutableListOf<PlayerIntent>()

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

    @Test
    fun readyState_subtitleMenuOpen_backPress_closesMenuInsteadOfExiting() =
        runAndroidComposeUiTest<ComponentActivity> {
            var backCalled = false
            var closeMenuCalled = false
            setContent {
                PlayerScreen(
                    state = PlayerUiState.Ready(
                        isPlaying = true,
                        positionMs = 0L,
                        durationMs = 100_000L,
                        controlsVisible = true,
                        subtitleTracks = persistentListOf(
                            SubtitleTrackUi(index = 2, language = "eng", title = "English", isDefault = true),
                        ),
                        selectedSubtitleIndex = 2,
                        subtitleMenuOpen = true,
                    ),
                    onBack = { backCalled = true },
                    player = mockPlayer,
                    onIntent = { intent -> if (intent == PlayerIntent.CloseSubtitleMenu) closeMenuCalled = true },
                )
            }

            activity?.onBackPressedDispatcher?.onBackPressed()
            waitForIdle()

            assertTrue(closeMenuCalled)
            assertFalse(backCalled)
        }

    @Test
    fun readyState_subtitleMenuClosed_backPress_invokesOnBack() = runAndroidComposeUiTest<ComponentActivity> {
        var backCalled = false
        setContent {
            PlayerScreen(
                state = PlayerUiState.Ready(
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = 100_000L,
                    controlsVisible = true,
                ),
                onBack = { backCalled = true },
                player = mockPlayer,
            )
        }

        activity?.onBackPressedDispatcher?.onBackPressed()
        waitForIdle()

        assertTrue(backCalled)
    }

    @Test
    fun readyState_buffering_showsSpinnerOnlyAfterOneSecond() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            PlayerScreen(
                state = PlayerUiState.Ready(
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 100_000L,
                    controlsVisible = false,
                    isBuffering = true,
                ),
                onBack = {},
                player = mockPlayer,
            )
        }
        val spinner = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )

        mainClock.advanceTimeBy(500L)
        onNode(spinner).assertDoesNotExist()

        mainClock.advanceTimeBy(600L)
        onNode(spinner).assertIsDisplayed()
    }

    private fun press(
        keyCode: Int,
        controlsVisible: Boolean,
        action: Int = NativeKeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
    ): Boolean = handlePlayerKeyEvent(
        ComposeKeyEvent(NativeKeyEvent(0L, 0L, action, keyCode, repeatCount)),
        controlsVisible,
    ) { intents += it }

    @Test
    fun handlePlayerKeyEvent_mediaPlayPause_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, controlsVisible = true)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaPlay_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_PLAY, controlsVisible = true)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaPause_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_PAUSE, controlsVisible = true)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaPlayPause_withHiddenControls_stillTogglesInsteadOfJustShowing() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaFastForward_withHiddenControls_stillSeeksInsteadOfJustShowing() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekForward()), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaFastForward_seeksForward() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, controlsVisible = true)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekForward()), intents)
    }

    @Test
    fun handlePlayerKeyEvent_mediaRewind_seeksBackward() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_REWIND, controlsVisible = true)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekBackward()), intents)
    }

    @Test
    fun handlePlayerKeyEvent_keyUp_isIgnored() {
        val consumed = press(
            NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            controlsVisible = true,
            action = NativeKeyEvent.ACTION_UP,
        )

        assertFalse(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handlePlayerKeyEvent_otherKeyWithHiddenControls_showsControls() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_DOWN, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.ShowControls), intents)
    }

    @Test
    fun handlePlayerKeyEvent_otherKeyWithVisibleControls_isNotConsumed() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_DOWN, controlsVisible = true)

        assertFalse(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handlePlayerKeyEvent_centerWithHiddenControls_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_CENTER, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handlePlayerKeyEvent_leftWithHiddenControls_seeksBackward() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekBackward()), intents)
    }

    @Test
    fun handlePlayerKeyEvent_rightWithHiddenControls_seeksForward() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_RIGHT, controlsVisible = false)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekForward()), intents)
    }

    @Test
    fun handlePlayerKeyEvent_heldCenterWithHiddenControls_doesNotToggleAgain() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_CENTER, controlsVisible = false, repeatCount = 1)

        assertTrue(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handlePlayerKeyEvent_heldMediaPlayPause_doesNotToggleAgain() {
        val consumed = press(NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, controlsVisible = true, repeatCount = 3)

        assertTrue(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handlePlayerKeyEvent_heldRightWithHiddenControls_passesRepeatCount() {
        press(NativeKeyEvent.KEYCODE_DPAD_RIGHT, controlsVisible = false, repeatCount = 7)

        assertEquals(listOf(PlayerIntent.SeekForward(repeatCount = 7)), intents)
    }
}
