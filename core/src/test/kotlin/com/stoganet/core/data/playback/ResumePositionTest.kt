package com.stoganet.core.data.playback

import com.stoganet.core.api.model.WatchProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResumePositionTest {

    @Test
    fun `partly watched resumes at its position`() {
        assertEquals(60_000L, WatchProgress(positionMs = 60_000L, played = false).resumePositionMs())
    }

    @Test
    fun `watched starts from the beginning`() {
        assertEquals(0L, WatchProgress(positionMs = 2_400_000L, played = true).resumePositionMs())
    }

    @Test
    fun `never watched starts from the beginning`() {
        assertEquals(0L, (null as WatchProgress?).resumePositionMs())
    }
}
