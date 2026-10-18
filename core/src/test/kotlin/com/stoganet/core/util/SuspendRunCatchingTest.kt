package com.stoganet.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class SuspendRunCatchingTest {

    @Test
    fun `returns the value as success`() = runTest {
        assertEquals(Result.success(1), suspendRunCatching { 1 })
    }

    @Test
    fun `wraps an error as failure`() = runTest {
        val result = suspendRunCatching { throw IllegalStateException("offline") }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `a timeout inside the call becomes a failure`() = runTest {
        val result = suspendRunCatching { withTimeout(10.milliseconds) { awaitCancellation() } }

        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelling the caller stops it instead of returning a failure`() = runTest {
        var result: Result<Unit>? = null
        val caller = launch { result = suspendRunCatching { awaitCancellation() } }
        runCurrent()

        caller.cancelAndJoin()

        assertNull(result)
    }
}
