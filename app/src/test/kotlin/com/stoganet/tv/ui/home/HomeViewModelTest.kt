package com.stoganet.tv.ui.home

import com.stoganet.tv.R
import com.stoganet.tv.api.model.HomeResponse
import com.stoganet.tv.api.model.HomeSection
import com.stoganet.tv.api.model.LibraryItem
import com.stoganet.tv.api.model.MediaState
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.home.HomeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val repository = mockk<HomeRepository>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeItem(id: String = "tmdb:movie:1") = LibraryItem(
        id = id,
        title = "Movie",
        year = 2020,
        type = MediaType.MOVIE,
        poster = "https://img/$id",
        overview = "Overview",
        state = MediaState.PLAYABLE,
    )

    private fun fakeResponse(vararg sectionIds: String) = HomeResponse(
        sections = sectionIds.map { id ->
            HomeSection(id = id, items = listOf(fakeItem()), hasMore = false)
        },
    )

    @Test
    fun `init emits Content on success`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("recently_added_movies"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        assertInstanceOf(HomeUiState.Content::class.java, vm.state.value)
        val content = vm.state.value as HomeUiState.Content
        assertEquals(1, content.sections.size)
        assertEquals("recently_added_movies", content.sections[0].id)
    }

    @Test
    fun `init emits Error on failure`() = runTest {
        coEvery { repository.getHome() } returns Result.failure(RuntimeException("network"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(HomeUiState.Error, vm.state.value)
    }

    @Test
    fun `Retry intent reloads`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("all_movies"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()
        vm.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getHome() }
    }

    @Test
    fun `known section IDs map to correct string resources`() = runTest {
        coEvery { repository.getHome() } returns Result.success(
            fakeResponse("recently_added_movies", "recently_added_tv", "all_movies", "all_tv"),
        )

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val sections = (vm.state.value as HomeUiState.Content).sections
        assertEquals(R.string.home_section_recently_added_movies, sections[0].titleRes)
        assertEquals(R.string.home_section_recently_added_tv, sections[1].titleRes)
        assertEquals(R.string.home_section_all_movies, sections[2].titleRes)
        assertEquals(R.string.home_section_all_tv, sections[3].titleRes)
    }

    @Test
    fun `unknown section ID maps to fallback string resource`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("some_future_section"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val section = (vm.state.value as HomeUiState.Content).sections[0]
        assertEquals(R.string.home_section_unknown, section.titleRes)
    }

    @Test
    fun `all_movies section has seeMoreRoute library-movies`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("all_movies"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val section = (vm.state.value as HomeUiState.Content).sections[0]
        assertEquals("library/movies", section.seeMoreRoute)
    }

    @Test
    fun `all_tv section has seeMoreRoute library-tv`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("all_tv"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val section = (vm.state.value as HomeUiState.Content).sections[0]
        assertEquals("library/tv", section.seeMoreRoute)
    }

    @Test
    fun `recently_added_movies section has null seeMoreRoute`() = runTest {
        coEvery { repository.getHome() } returns Result.success(fakeResponse("recently_added_movies"))

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val section = (vm.state.value as HomeUiState.Content).sections[0]
        assertNull(section.seeMoreRoute)
    }

    @Test
    fun `item contentDescription includes title and year`() = runTest {
        val matrixItem = LibraryItem(
            id = "tmdb:movie:603",
            title = "The Matrix",
            year = 1999,
            type = MediaType.MOVIE,
            poster = "https://img/1",
            overview = "Desc",
            state = MediaState.PLAYABLE,
        )
        coEvery { repository.getHome() } returns Result.success(
            HomeResponse(
                sections = listOf(
                    HomeSection(id = "all_movies", items = listOf(matrixItem), hasMore = false),
                ),
            ),
        )

        val vm = HomeViewModel(repository)
        advanceUntilIdle()

        val item = (vm.state.value as HomeUiState.Content).sections[0].items[0]
        assertEquals("The Matrix (1999)", item.contentDescription)
    }
}
