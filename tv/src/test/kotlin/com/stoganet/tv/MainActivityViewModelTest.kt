package com.stoganet.tv

import com.stoganet.core.data.auth.AuthRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {

    private val signedIn = MutableSharedFlow<Boolean>()
    private val repository = mockk<AuthRepository> { every { isSignedIn } returns signedIn }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state is Loading until sign in status is known`() {
        val viewModel = MainActivityViewModel(repository)

        assertEquals(MainActivityUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `state is SignedIn when signed in`() = runTest {
        val viewModel = MainActivityViewModel(repository)

        signedIn.emit(true)

        assertEquals(MainActivityUiState.SignedIn, viewModel.state.value)
    }

    @Test
    fun `state is SignedOut when signed out`() = runTest {
        val viewModel = MainActivityViewModel(repository)

        signedIn.emit(false)

        assertEquals(MainActivityUiState.SignedOut, viewModel.state.value)
    }

    @Test
    fun `state follows sign out after sign in`() = runTest {
        val viewModel = MainActivityViewModel(repository)

        signedIn.emit(true)
        signedIn.emit(false)

        assertEquals(MainActivityUiState.SignedOut, viewModel.state.value)
    }
}
