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

    private val intents = mutableListOf<PlayerIntent>()

    private fun press(keyCode: Int, action: Int = NativeKeyEvent.ACTION_DOWN): Boolean =
        handleSeekBarKeyEvent(ComposeKeyEvent(NativeKeyEvent(action, keyCode))) { intents += it }

    @Test
    fun handleSeekBarKeyEvent_left_seeksBackward() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_LEFT)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekBackward), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_right_seeksForward() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_RIGHT)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.SeekForward), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_center_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_enter_togglesPlayPause() {
        val consumed = press(NativeKeyEvent.KEYCODE_ENTER)

        assertTrue(consumed)
        assertEquals(listOf(PlayerIntent.TogglePlayPause), intents)
    }

    @Test
    fun handleSeekBarKeyEvent_keyUp_isIgnored() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_RIGHT, NativeKeyEvent.ACTION_UP)

        assertFalse(consumed)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun handleSeekBarKeyEvent_down_isNotConsumed() {
        val consumed = press(NativeKeyEvent.KEYCODE_DPAD_DOWN)

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
