package com.stoganet.tv.data.detail

import com.stoganet.tv.api.model.CastMember
import com.stoganet.tv.api.model.LibraryDetail
import com.stoganet.tv.api.model.MediaState
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.api.model.PlayInfo
import com.stoganet.tv.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class DetailRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val repository = DetailRepository(api)

    private fun fakeDetail() = LibraryDetail(
        id = "tmdb:movie:603",
        title = "The Matrix",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        backdrop = "https://img/backdrop",
        overview = "A computer hacker learns the truth.",
        state = MediaState.PLAYABLE,
        genres = listOf("Action", "Sci-Fi"),
        runtime = 136,
        cast = listOf(CastMember(name = "Keanu Reeves", role = "Actor")),
        seasons = emptyList(),
        play = PlayInfo(streamUrl = "https://api.stoganet.com/stream/jf-uuid"),
    )

    @Test
    fun `returns success wrapping detail on success`() = runTest {
        coEvery { api.getDetail("tmdb:movie:603") } returns fakeDetail()

        val result = repository.getDetail("tmdb:movie:603")

        assertInstanceOf(Result::class.java, result)
        assertEquals("The Matrix", result.getOrThrow().title)
    }

    @Test
    fun `returns failure when api throws`() = runTest {
        coEvery { api.getDetail(any()) } throws RuntimeException("network error")

        val result = repository.getDetail("tmdb:movie:603")

        assert(result.isFailure)
    }
}
