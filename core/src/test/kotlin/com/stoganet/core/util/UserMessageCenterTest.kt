package com.stoganet.core.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserMessageCenterTest {

    private val center = UserMessageCenter()

    @Test
    fun `show emits the resource id to collectors`() = runTest {
        center.show(42)

        assertEquals(42, center.messages.first())
    }

    @Test
    fun `multiple shows are delivered in order`() = runTest {
        center.show(1)
        center.show(2)

        val first = center.messages.first()
        assertEquals(1, first)
    }
}
