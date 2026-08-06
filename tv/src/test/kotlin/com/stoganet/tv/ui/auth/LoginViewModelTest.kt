package com.stoganet.tv.ui.auth

import androidx.lifecycle.viewModelScope
import com.stoganet.core.api.model.TokenPair
import com.stoganet.core.api.model.User
import com.stoganet.core.data.auth.AuthRepository
import com.stoganet.core.data.auth.FakeDataStore
import com.stoganet.core.data.auth.LoginResult
import com.stoganet.core.data.auth.TokenStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val repository = mockk<AuthRepository>()
    private lateinit var tokenStore: TokenStore

    @BeforeEach
    fun setUp() {
        tokenStore = TokenStore(FakeDataStore())
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Submit with blank username does not call repository`() = runTest {
        val viewModel = LoginViewModel(repository, tokenStore)

        viewModel.onIntent(LoginIntent.Submit(username = "", password = "pass"))

        coVerify(exactly = 0) { repository.login(any(), any(), any()) }
        assertEquals(LoginUiState.Status.Idle, viewModel.state.value.status)
    }

    @Test
    fun `Submit success saves tokens to tokenStore`() = runTest {
        val pair = TokenPair(
            accessToken = "at",
            refreshToken = "rt",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        coEvery { repository.login("user", "pass", any()) } returns Result.success(LoginResult.Success(pair))

        val viewModel = LoginViewModel(repository, tokenStore)
        viewModel.onIntent(LoginIntent.Submit(username = "user", password = "pass"))
        advanceUntilIdle()

        assertEquals("at", tokenStore.accessToken())

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `Submit InvalidCredentials emits CredentialsError status`() = runTest {
        coEvery { repository.login("user", "wrong", any()) } returns Result.success(LoginResult.InvalidCredentials)

        val viewModel = LoginViewModel(repository, tokenStore)
        viewModel.onIntent(LoginIntent.Submit(username = "user", password = "wrong"))
        advanceUntilIdle()

        assertEquals(LoginUiState.Status.CredentialsError, viewModel.state.value.status)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `Submit NetworkError result emits NetworkError status`() = runTest {
        coEvery { repository.login("user", "pass", any()) } returns Result.success(LoginResult.NetworkError)

        val viewModel = LoginViewModel(repository, tokenStore)
        viewModel.onIntent(LoginIntent.Submit(username = "user", password = "pass"))
        advanceUntilIdle()

        assertEquals(LoginUiState.Status.NetworkError, viewModel.state.value.status)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `Submit repository failure emits NetworkError status`() = runTest {
        coEvery { repository.login("user", "pass", any()) } returns Result.failure(RuntimeException("boom"))

        val viewModel = LoginViewModel(repository, tokenStore)
        viewModel.onIntent(LoginIntent.Submit(username = "user", password = "pass"))
        advanceUntilIdle()

        assertEquals(LoginUiState.Status.NetworkError, viewModel.state.value.status)

        viewModel.viewModelScope.cancel()
    }
}
