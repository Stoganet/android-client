package com.stoganet.core.data.playback

import app.cash.turbine.test
import com.stoganet.core.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val repository = PlaybackRepository(api)

    @Test
    fun `returns success and forwards args on success`() = runTest {
        coEvery { api.reportProgress("jf-uuid", 5000L, false) } returns Unit

        val result = repository.reportProgress("jf-uuid", 5000L, false)

        assert(result.isSuccess)
        coVerify { api.reportProgress("jf-uuid", 5000L, false) }
    }

    @Test
    fun `saved progress becomes the last progress`() = runTest {
        coEvery { api.reportProgress("jf-uuid", 5000L, false) } returns Unit

        repository.reportProgress("jf-uuid", 5000L, false)

        assertEquals(ProgressUpdate("jf-uuid", 5000L, false), repository.lastProgress.value)
    }

    @Test
    fun `failed report leaves the last progress unchanged`() = runTest {
        coEvery { api.reportProgress(any(), any(), any()) } throws RuntimeException("network error")

        repository.reportProgress("jf-uuid", 5000L, false)

        assertNull(repository.lastProgress.value)
    }

    @Test
    fun `returns failure when api throws`() = runTest {
        coEvery { api.reportProgress(any(), any(), any()) } throws RuntimeException("network error")

        val result = repository.reportProgress("jf-uuid", 5000L, false)

        assert(result.isFailure)
    }

    @Test
    fun `saved progress sends the progress saved signal`() = runTest {
        coEvery { api.reportProgress(any(), any(), any()) } returns Unit

        repository.progressSaved.test {
            repository.reportProgress("jf-uuid", 5000L, false)
            awaitItem()
        }
    }

    @Test
    fun `failed report sends no progress saved signal`() = runTest {
        coEvery { api.reportProgress(any(), any(), any()) } throws RuntimeException("network error")

        repository.progressSaved.test {
            repository.reportProgress("jf-uuid", 5000L, false)
            expectNoEvents()
        }
    }

    @Test
    fun `a save made before anyone listens is not replayed`() = runTest {
        coEvery { api.reportProgress(any(), any(), any()) } returns Unit
        repository.reportProgress("jf-uuid", 5000L, false)

        repository.progressSaved.test {
            expectNoEvents()
        }
    }
}
