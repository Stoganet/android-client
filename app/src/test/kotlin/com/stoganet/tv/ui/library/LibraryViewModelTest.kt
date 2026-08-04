package com.stoganet.tv.ui.library

import com.stoganet.tv.api.model.LibraryItem
import com.stoganet.tv.api.model.LibraryListResponse
import com.stoganet.tv.api.model.MediaState
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.library.LibraryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val repository = mockk<LibraryRepository>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeItem(id: String = "1") = LibraryItem(
        id = id,
        title = "Movie $id",
        year = 2020,
        type = MediaType.MOVIE,
        poster = "https://img/$id",
        overview = "Overview",
        state = MediaState.PLAYABLE,
    )

    private fun fakeResponse(
        items: List<LibraryItem> = listOf(fakeItem()),
        total: Int = 1,
        nextCursor: String? = null,
    ) = LibraryListResponse(items = items, total = total, nextCursor = nextCursor)

    @Test
    fun `loads content on success`() = runTest {
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.success(fakeResponse())
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        val state = vm.state.value
        assertInstanceOf(LibraryUiState.Content::class.java, state)
        assertEquals(1, (state as LibraryUiState.Content).items.size)
    }

    @Test
    fun `shows Error on failure`() = runTest {
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.failure(RuntimeException("fail"))
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        assertInstanceOf(LibraryUiState.Error::class.java, vm.state.value)
    }

    @Test
    fun `Retry reloads from scratch`() = runTest {
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.failure(RuntimeException("fail"))
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.success(fakeResponse())
        vm.onIntent(LibraryIntent.Retry)

        assertInstanceOf(LibraryUiState.Content::class.java, vm.state.value)
        coVerify(exactly = 2) { repository.getLibrary(MediaType.MOVIE, null, any()) }
    }

    @Test
    fun `LoadMore appends items and advances cursor`() = runTest {
        val page1 = fakeResponse(
            items = listOf(fakeItem("1")),
            total = 2,
            nextCursor = "cursor1",
        )
        val page2 = fakeResponse(
            items = listOf(fakeItem("2")),
            total = 2,
            nextCursor = null,
        )
        coEvery { repository.getLibrary(any(), null, any()) } returns Result.success(page1)
        coEvery { repository.getLibrary(any(), "cursor1", any()) } returns Result.success(page2)

        val vm = LibraryViewModel(MediaType.MOVIE, repository)
        vm.onIntent(LibraryIntent.LoadMore)

        val state = vm.state.value as LibraryUiState.Content
        assertEquals(2, state.items.size)
        assertFalse(state.hasMore)
    }

    @Test
    fun `LoadMore clears hasLoadMoreError when retry begins`() = runTest {
        val page1 = fakeResponse(items = listOf(fakeItem("1")), nextCursor = "cursor1")
        coEvery { repository.getLibrary(any(), null, any()) } returns Result.success(page1)
        coEvery { repository.getLibrary(any(), "cursor1", any()) } returns Result.failure(RuntimeException("fail"))

        val vm = LibraryViewModel(MediaType.MOVIE, repository)
        vm.onIntent(LibraryIntent.LoadMore)

        val errorState = vm.state.value as LibraryUiState.Content
        assert(errorState.hasLoadMoreError)

        coEvery { repository.getLibrary(any(), "cursor1", any()) } returns Result.success(
            fakeResponse(items = listOf(fakeItem("2")), nextCursor = null),
        )
        vm.onIntent(LibraryIntent.LoadMore)

        val retryingState = vm.state.value as LibraryUiState.Content
        assertFalse(retryingState.hasLoadMoreError)
    }

    @Test
    fun `LoadMore no-ops when not in Content state`() = runTest {
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.failure(RuntimeException("fail"))
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        vm.onIntent(LibraryIntent.LoadMore)

        coVerify(exactly = 1) { repository.getLibrary(any(), any(), any()) }
    }

    @Test
    fun `LoadMore no-ops when hasMore is false`() = runTest {
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.success(
            fakeResponse(nextCursor = null),
        )
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        vm.onIntent(LibraryIntent.LoadMore)

        coVerify(exactly = 1) { repository.getLibrary(any(), any(), any()) }
    }

    @Test
    fun `type arg drives API query param`() = runTest {
        coEvery { repository.getLibrary(MediaType.TV, any(), any()) } returns Result.success(fakeResponse())
        val vm = LibraryViewModel(MediaType.TV, repository)

        assertInstanceOf(LibraryUiState.Content::class.java, vm.state.value)
        coVerify { repository.getLibrary(MediaType.TV, null, any()) }
    }

    @Test
    fun `LoadMore no-ops when already loading more`() = runTest {
        val deferred = CompletableDeferred<Result<LibraryListResponse>>()
        val page1 = fakeResponse(items = listOf(fakeItem("1")), nextCursor = "cursor1")
        coEvery { repository.getLibrary(any(), null, any()) } returns Result.success(page1)
        coEvery { repository.getLibrary(any(), "cursor1", any()) } coAnswers { deferred.await() }

        val vm = LibraryViewModel(MediaType.MOVIE, repository)
        vm.onIntent(LibraryIntent.LoadMore) // isLoadingMore = true, suspended
        vm.onIntent(LibraryIntent.LoadMore) // no-ops: isLoadingMore guard fires

        coVerify(exactly = 2) { repository.getLibrary(any(), any(), any()) } // init + first LoadMore only
        deferred.cancel()
    }

    @Test
    fun `Retry no-ops when already Loading`() = runTest {
        val deferred = CompletableDeferred<Result<LibraryListResponse>>()
        coEvery { repository.getLibrary(any(), any(), any()) } coAnswers { deferred.await() }
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        vm.onIntent(LibraryIntent.Retry) // no-ops: Loading guard fires

        deferred.cancel()
        coVerify(exactly = 1) { repository.getLibrary(any(), any(), any()) }
    }

    @Test
    fun `contentDescription format is title (year)`() = runTest {
        val item = fakeItem("1").copy(title = "Inception", year = 2010)
        coEvery { repository.getLibrary(any(), any(), any()) } returns Result.success(
            fakeResponse(items = listOf(item)),
        )
        val vm = LibraryViewModel(MediaType.MOVIE, repository)

        val content = vm.state.value as LibraryUiState.Content
        assertEquals("Inception (2010)", content.items[0].contentDescription)
    }
}
