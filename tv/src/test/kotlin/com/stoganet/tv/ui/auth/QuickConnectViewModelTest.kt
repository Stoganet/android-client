package com.stoganet.tv.ui.auth

import androidx.lifecycle.viewModelScope
import com.stoganet.core.api.model.QuickConnectStartResponse
import com.stoganet.core.api.model.TokenPair
import com.stoganet.core.api.model.User
import com.stoganet.core.data.auth.AuthRepository
import com.stoganet.core.data.auth.FakeDataStore
import com.stoganet.core.data.auth.QuickConnectPollResult
import com.stoganet.core.data.auth.TokenStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class QuickConnectViewModelTest {

    private val repository = mockk<AuthRepository>()
    private lateinit var tokenStore: TokenStore

    @BeforeEach
    fun setUp() {
        tokenStore = TokenStore(FakeDataStore())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setMainDispatcher(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    }

    @Test
    fun `init emits WaitingForApproval with code`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returns Result.success(
            QuickConnectStartResponse(code = "ABC123", pollToken = "tok"),
        )
        coEvery { repository.pollQuickConnect(any()) } returns Result.success(QuickConnectPollResult.Pending)

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        try {
            assertEquals("ABC123", viewModel.state.value.code)
            assertEquals(QuickConnectUiState.Status.WaitingForApproval, viewModel.state.value.status)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `poll Success saves tokens to tokenStore`() = runTest {
        setMainDispatcher(testScheduler)
        val pair = TokenPair(
            accessToken = "at",
            refreshToken = "rt",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        coEvery { repository.startQuickConnect() } returns Result.success(
            QuickConnectStartResponse(code = "ABC123", pollToken = "tok"),
        )
        coEvery { repository.pollQuickConnect("tok") } returns Result.success(QuickConnectPollResult.Success(pair))

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        advanceUntilIdle()

        assertEquals("at", tokenStore.accessToken())
    }

    @Test
    fun `poll Expired emits Expired status`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returns Result.success(
            QuickConnectStartResponse(code = "ABC123", pollToken = "tok"),
        )
        coEvery { repository.pollQuickConnect("tok") } returns Result.success(QuickConnectPollResult.Expired)

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        advanceUntilIdle()

        assertEquals(QuickConnectUiState.Status.Expired, viewModel.state.value.status)
    }

    @Test
    fun `poll failure emits Error status`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returns Result.success(
            QuickConnectStartResponse(code = "ABC123", pollToken = "tok"),
        )
        coEvery { repository.pollQuickConnect("tok") } returns Result.failure(RuntimeException("unexpected"))

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        advanceUntilIdle()

        assertEquals(QuickConnectUiState.Status.Error, viewModel.state.value.status)
    }

    @Test
    fun `startQuickConnect failure emits Error status`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returns Result.failure(RuntimeException("network"))

        val viewModel = QuickConnectViewModel(repository, tokenStore)

        assertEquals(QuickConnectUiState.Status.Error, viewModel.state.value.status)
    }

    @Test
    fun `Retry intent restarts auth flow`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returns Result.success(
            QuickConnectStartResponse(code = "ABC123", pollToken = "tok"),
        )
        coEvery { repository.pollQuickConnect(any()) } returns Result.success(QuickConnectPollResult.Pending)

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        viewModel.onIntent(QuickConnectIntent.Retry)

        try {
            coVerify(exactly = 2) { repository.startQuickConnect() }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `Retry stops polling the old code`() = runTest {
        setMainDispatcher(testScheduler)
        coEvery { repository.startQuickConnect() } returnsMany listOf(
            Result.success(QuickConnectStartResponse(code = "OLD111", pollToken = "old")),
            Result.success(QuickConnectStartResponse(code = "NEW222", pollToken = "new")),
        )
        coEvery { repository.pollQuickConnect(any()) } returns Result.success(QuickConnectPollResult.Pending)

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        try {
            advanceTimeBy(3_500.milliseconds)
            viewModel.onIntent(QuickConnectIntent.Retry)
            advanceTimeBy(10_000.milliseconds)

            coVerify(exactly = 2) { repository.pollQuickConnect("old") }
            assertEquals("NEW222", viewModel.state.value.code)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `Retry while the code is still loading drops the old attempt`() = runTest {
        setMainDispatcher(testScheduler)
        val firstStart = CompletableDeferred<Result<QuickConnectStartResponse>>()
        coEvery { repository.startQuickConnect() } coAnswers { firstStart.await() } coAndThen {
            Result.success(QuickConnectStartResponse(code = "NEW222", pollToken = "new"))
        }
        coEvery { repository.pollQuickConnect(any()) } returns Result.success(QuickConnectPollResult.Pending)

        val viewModel = QuickConnectViewModel(repository, tokenStore)
        try {
            viewModel.onIntent(QuickConnectIntent.Retry)
            firstStart.complete(Result.success(QuickConnectStartResponse(code = "OLD111", pollToken = "old")))
            advanceTimeBy(10_000)

            assertEquals("NEW222", viewModel.state.value.code)
            coVerify(exactly = 0) { repository.pollQuickConnect("old") }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
