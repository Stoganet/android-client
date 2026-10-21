package com.stoganet.core.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

class FormattingTest {

    private val defaultLocale = Locale.getDefault()

    @BeforeEach fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @AfterEach fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `formatRuntime formats hours and minutes`() {
        assertEquals("2h 16m", formatRuntime(136))
    }

    @Test
    fun `formatRuntime formats minutes only`() {
        assertEquals("45m", formatRuntime(45))
    }

    @Test
    fun `formatRuntime formats whole hours`() {
        assertEquals("2h", formatRuntime(120))
    }

    @Test
    fun `formatRuntime returns empty string for zero`() {
        assertEquals("", formatRuntime(0))
    }

    @Test
    fun `formatAirDate formats an ISO date in the medium localized style`() {
        assertEquals("Feb 10, 2008", formatAirDate("2008-02-10"))
    }

    @Test
    fun `formatAirDate returns null for an unparseable date`() {
        assertNull(formatAirDate("not-a-date"))
    }

    @Test
    fun `formatAirDate returns null for null`() {
        assertNull(formatAirDate(null))
    }
}
