package com.stoganet.tv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusAfterFirstFrameTest {

    @Test
    fun focusesTarget_onlyAfterFirstFrame() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            val target = remember { FocusRequester() }
            Box(Modifier.size(10.dp).testTag("target").focusRequester(target).focusable())
            FocusAfterFirstFrame(target = target, enabled = true)
        }

        onNodeWithTag("target").assertIsNotFocused()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()

        onNodeWithTag("target").assertIsFocused()
    }

    @Test
    fun doesNothing_whenDisabled() = runComposeUiTest {
        setContent {
            val target = remember { FocusRequester() }
            Box(Modifier.size(10.dp).testTag("target").focusRequester(target).focusable())
            FocusAfterFirstFrame(target = target, enabled = false)
        }
        waitForIdle()

        onNodeWithTag("target").assertIsNotFocused()
    }
}
