package com.stoganet.tv.data.library

import com.stoganet.tv.api.model.LibraryListResponse
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val repository = LibraryRepository(api)

    private fun emptyResponse() = LibraryListResponse(
        items = emptyList(),
        total = 0,
        nextCursor = null,
    )

    @Test
    fun `getLibrary returns success when api succeeds`() = runTest {
        coEvery { api.getLibrary(any(), any(), any()) } returns emptyResponse()

        val result = repository.getLibrary(limit = 100)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getLibrary returns failure when api throws`() = runTest {
        coEvery { api.getLibrary(any(), any(), any()) } throws RuntimeException("error")

        val result = repository.getLibrary(limit = 100)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getLibrary forwards type cursor and limit to api`() = runTest {
        coEvery { api.getLibrary(any(), any(), any()) } returns emptyResponse()

        repository.getLibrary(type = MediaType.MOVIE, cursor = "abc", limit = 50)

        coVerify { api.getLibrary(MediaType.MOVIE, "abc", 50) }
    }
}
