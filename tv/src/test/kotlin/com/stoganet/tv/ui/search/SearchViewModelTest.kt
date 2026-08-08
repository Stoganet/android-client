package com.stoganet.tv.ui.search

import com.stoganet.core.api.model.LibraryItem
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.data.net.SearchApiException
import com.stoganet.core.data.search.SearchRepository
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<SearchRepository>()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeItem(id: String = "tmdb:movie:603") = LibraryItem(
        id = id,
        title = "Test Movie",
        year = 1999,
        type = MediaType.MOVIE,
        poster = "https://img/poster",
        overview = "overview",
        state = MediaState.REQUESTABLE,
    )

    @Test
    fun `QueryChanged below 2 chars does not search after debounce`() = runTest(testDispatcher) {
        val vm = SearchViewModel(repository)
        vm.onIntent(SearchIntent.QueryChanged("n"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any()) }
    }

    @Test
    fun `QueryChanged at 2 or more chars searches after debounce elapses`() = runTest(testDispatcher) {
        coEvery { repository.search("ne") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("ne"))
        testDispatcher.scheduler.advanceTimeBy(399)
        coVerify(exactly = 0) { repository.search(any()) }

        testDispatcher.scheduler.advanceTimeBy(2)
        testDispatcher.scheduler.runCurrent()
        coVerify { repository.search("ne") }
    }

    @Test
    fun `rapid QueryChanged calls only fire one search for the latest text`() = runTest(testDispatcher) {
        coEvery { repository.search(any()) } returns Result.success(emptyList())
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("ne"))
        testDispatcher.scheduler.advanceTimeBy(100)
        vm.onIntent(SearchIntent.QueryChanged("neo"))
        testDispatcher.scheduler.advanceTimeBy(100)
        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.search("movie") }
        coVerify(exactly = 0) { repository.search("ne") }
        coVerify(exactly = 0) { repository.search("neo") }
    }

    @Test
    fun `Submit fires immediately without waiting for debounce`() = runTest(testDispatcher) {
        coEvery { repository.search("movie") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("movie"))
        vm.onIntent(SearchIntent.Submit)
        testDispatcher.scheduler.runCurrent()

        coVerify { repository.search("movie") }
    }

    @Test
    fun `Submit cancels the pending debounced search`() = runTest(testDispatcher) {
        coEvery { repository.search("movie") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceTimeBy(100)
        vm.onIntent(SearchIntent.Submit)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.search("movie") }
    }

    @Test
    fun `success with items updates state to Results`() = runTest(testDispatcher) {
        coEvery { repository.search("movie") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertInstanceOf(SearchUiState.Results::class.java, state)
        state as SearchUiState.Results
        assertEquals(1, state.items.size)
    }

    @Test
    fun `success with empty list updates state to NoResults`() = runTest(testDispatcher) {
        coEvery { repository.search("zzz") } returns Result.success(emptyList())
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("zzz"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(SearchUiState.NoResults::class.java, vm.state.value)
    }

    @Test
    fun `429 failure keeps state unchanged`() = runTest(testDispatcher) {
        coEvery { repository.search("movie") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)
        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBefore = vm.state.value

        coEvery { repository.search("movie2") } returns
            Result.failure(SearchApiException(HttpStatusCode.TooManyRequests))
        vm.onIntent(SearchIntent.QueryChanged("movie2"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(stateBefore, vm.state.value)
    }

    @Test
    fun `503 failure updates state to Error`() = runTest(testDispatcher) {
        coEvery { repository.search("movie") } returns
            Result.failure(SearchApiException(HttpStatusCode.ServiceUnavailable))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(SearchUiState.Error::class.java, vm.state.value)
    }

    @Test
    fun `stale response does not overwrite newer result`() = runTest(testDispatcher) {
        val staleDeferred = CompletableDeferred<Result<List<LibraryItem>>>()
        coEvery { repository.search("ne") } coAnswers { staleDeferred.await() }
        coEvery { repository.search("movie") } returns Result.success(listOf(fakeItem()))
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged("ne"))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(SearchIntent.QueryChanged("movie"))
        testDispatcher.scheduler.advanceUntilIdle()

        staleDeferred.complete(Result.success(listOf(fakeItem(id = "stale"))))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertInstanceOf(SearchUiState.Results::class.java, state)
        state as SearchUiState.Results
        assertEquals("tmdb:movie:603", state.items.first().id)
    }

    @Test
    fun `blank query resets to Empty immediately without api call`() = runTest(testDispatcher) {
        val vm = SearchViewModel(repository)

        vm.onIntent(SearchIntent.QueryChanged(""))

        assertTrue(vm.state.value is SearchUiState.Empty)
        coVerify(exactly = 0) { repository.search(any()) }
    }
}
