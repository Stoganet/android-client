package com.stoganet.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerControlsTest {

    private fun composeKeyEvent(nativeKeyCode: Int, action: Int = NativeKeyEvent.ACTION_DOWN) =
        ComposeKeyEvent(NativeKeyEvent(action, nativeKeyCode))

    @Test
    fun handleSeekBarKeyEvent_left_seeksBackward() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_LEFT)) { intents += it }

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekBackward), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_right_seeksForward() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_RIGHT)) { intents += it }

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekForward), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_center_togglesPlayPause() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_CENTER)) { intents += it }

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_enter_togglesPlayPause() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(composeKeyEvent(NativeKeyEvent.KEYCODE_ENTER)) { intents += it }

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_keyUp_isIgnored() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(
            composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_RIGHT, NativeKeyEvent.ACTION_UP),
        ) { intents += it }

        assertFalse(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handleSeekBarKeyEvent_down_isNotConsumed() {
        val intents = mutableListOf<PlayerIntent>()

        val consumed = handleSeekBarKeyEvent(composeKeyEvent(NativeKeyEvent.KEYCODE_DPAD_DOWN)) { intents += it }

        assertFalse(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun formatPlaybackTime_withoutHours() {
        assertEquals("0:00", formatPlaybackTime(0L, withHours = false))
        assertEquals("1:05", formatPlaybackTime(65_000L, withHours = false))
    }

    @Test
    fun formatPlaybackTime_withHours() {
        assertEquals("0:01:05", formatPlaybackTime(65_000L, withHours = true))
        assertEquals("1:02:05", formatPlaybackTime(3_725_000L, withHours = true))
    }
}
