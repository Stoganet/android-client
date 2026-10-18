package com.stoganet.core.data.detail

import app.cash.turbine.test
import com.stoganet.core.api.model.CastMember
import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.data.playback.PlaybackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class DetailRepositoryTest {

    private val api = mockk<StoganetApi>()
    private val progressSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val playbackRepository = mockk<PlaybackRepository> {
        every { progressSaved } returns
            this@DetailRepositoryTest.progressSaved
    }
    private val repository = DetailRepository(api, playbackRepository)

    private fun fakeDetail() = LibraryDetail(
        id = "tmdb:movie:603",
        title = "The Matrix",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        backdrop = "https://img/backdrop",
        thumbHasLogo = false,
        overview = "A computer hacker learns the truth.",
        state = MediaState.PLAYABLE,
        genres = listOf("Action", "Sci-Fi"),
        runtime = 136,
        cast = listOf(CastMember(name = "Keanu Reeves", role = "Actor")),
        seasons = emptyList(),
        play = PlayInfo(streamUrl = "https://api.stoganet.com/stream/jf-uuid", subtitleTracks = emptyList()),
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

    // progressSaved does not replay, so a signal sent before the flow listens would be lost
    private suspend fun awaitListener() = progressSaved.subscriptionCount.first { it > 0 }

    private fun fakeEpisode() = Episode(
        id = "ep1",
        number = 1,
        seasonNumber = 1,
        title = "Pilot",
        state = MediaState.PLAYABLE,
    )

    @Test
    fun `detail flow fetches when collected`() = runTest {
        coEvery { api.getDetail("tmdb:movie:603") } returns fakeDetail()

        repository.detail("tmdb:movie:603").test {
            assertEquals("The Matrix", awaitItem().getOrThrow().title)
        }
    }

    @Test
    fun `detail flow fetches again after progress is saved`() = runTest {
        coEvery { api.getDetail("tmdb:movie:603") } returns fakeDetail()

        repository.detail("tmdb:movie:603").test {
            awaitItem()
            awaitListener()
            progressSaved.emit(Unit)
            awaitItem()
        }
        coVerify(exactly = 2) { api.getDetail("tmdb:movie:603") }
    }

    @Test
    fun `detail flow passes a failed fetch on as a failure`() = runTest {
        coEvery { api.getDetail(any()) } throws RuntimeException("network error")

        repository.detail("tmdb:movie:603").test {
            assert(awaitItem().isFailure)
        }
    }

    @Test
    fun `episodes flow fetches again after progress is saved`() = runTest {
        coEvery { api.getEpisodes("tmdb:tv:1399", 1) } returns listOf(fakeEpisode())

        repository.episodes("tmdb:tv:1399", 1).test {
            assertEquals("Pilot", awaitItem().getOrThrow().single().title)
            awaitListener()
            progressSaved.emit(Unit)
            awaitItem()
        }
        coVerify(exactly = 2) { api.getEpisodes("tmdb:tv:1399", 1) }
    }

    @Test
    fun `progress saved during a fetch drops the older result`() = runTest {
        val oldFetch = CompletableDeferred<LibraryDetail>()
        coEvery {
            api.getDetail(
                "tmdb:movie:603",
            )
        } coAnswers { oldFetch.await() } andThen fakeDetail().copy(title = "Newer")

        repository.detail("tmdb:movie:603").test {
            awaitListener()
            progressSaved.emit(Unit)
            assertEquals("Newer", awaitItem().getOrThrow().title)
            oldFetch.complete(fakeDetail())
            expectNoEvents()
        }
    }
}
