package com.stoganet.core.data.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressFractionTest {

    @Test
    fun `is the position over the duration`() {
        assertEquals(0.5f, progressFraction(positionMs = 1_000L, durationMs = 2_000L), 0.001f)
    }

    @Test
    fun `is capped at one`() {
        assertEquals(1f, progressFraction(positionMs = 5_000L, durationMs = 2_000L), 0.001f)
    }

    @Test
    fun `is zero without a duration`() {
        assertEquals(0f, progressFraction(positionMs = 1_000L, durationMs = 0L), 0.001f)
    }

    @Test
    fun `is zero without a position`() {
        assertEquals(0f, progressFraction(positionMs = 0L, durationMs = 2_000L), 0.001f)
    }

    @Test
    fun `is zero for a negative position`() {
        assertEquals(0f, progressFraction(positionMs = -1_000L, durationMs = 2_000L), 0.001f)
    }
}
