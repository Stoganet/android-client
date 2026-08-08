package com.stoganet.core.data.search

import com.stoganet.core.api.model.LibraryItem
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.SearchResponse
import com.stoganet.core.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val repository = SearchRepository(api)

    private fun fakeItem() = LibraryItem(
        id = "tmdb:movie:603",
        title = "Test Movie",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        overview = "overview",
        state = MediaState.REQUESTABLE,
    )

    @Test
    fun `search returns items on success`() = runTest {
        coEvery { api.search("neon") } returns SearchResponse(items = listOf(fakeItem()))

        val result = repository.search("neon")

        assertTrue(result.isSuccess)
        assertEquals(listOf(fakeItem()), result.getOrThrow())
    }

    @Test
    fun `search returns failure when api throws`() = runTest {
        coEvery { api.search(any()) } throws RuntimeException("error")

        val result = repository.search("neon")

        assertTrue(result.isFailure)
    }

    @Test
    fun `requestMovie returns success on api success`() = runTest {
        coEvery { api.requestMovie("tmdb:movie:603") } returns Unit

        val result = repository.requestMovie("tmdb:movie:603")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `requestMovie returns failure when api throws`() = runTest {
        coEvery { api.requestMovie(any()) } throws RuntimeException("error")

        val result = repository.requestMovie("tmdb:movie:603")

        assertTrue(result.isFailure)
    }
}
