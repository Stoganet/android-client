package com.stoganet.tv.data.auth

import com.stoganet.tv.api.model.QuickConnectStartResponse
import com.stoganet.tv.api.model.TokenPair
import com.stoganet.tv.api.model.User
import com.stoganet.tv.data.net.StoganetApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthRepositoryTest {

    private val api = mockk<StoganetApi>()
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: AuthRepository

    private val fakeUser = User(id = "u1", email = "a@b.com", displayName = "Test")

    @BeforeEach
    fun setUp() {
        tokenStore = TokenStore(FakeDataStore())
        repository = AuthRepository(api, tokenStore)
    }

    @Test
    fun `startQuickConnect returns success with code and pollToken`() = runTest {
        coEvery { api.startQuickConnect() } returns QuickConnectStartResponse(code = "ABC123", pollToken = "tok-xyz")

        val result = repository.startQuickConnect()

        assertTrue(result.isSuccess)
        assertEquals("ABC123", result.getOrThrow().code)
        assertEquals("tok-xyz", result.getOrThrow().pollToken)
    }

    @Test
    fun `startQuickConnect returns failure when api throws`() = runTest {
        coEvery { api.startQuickConnect() } throws IllegalStateException("503")

        val result = repository.startQuickConnect()

        assertTrue(result.isFailure)
    }

    @Test
    fun `pollQuickConnect returns Success`() = runTest {
        val tokens = TokenPair(accessToken = "at", refreshToken = "rt", user = fakeUser)
        coEvery { api.pollQuickConnect("tok") } returns QuickConnectPollResult.Success(tokens)

        val result = repository.pollQuickConnect("tok")

        assertTrue(result.isSuccess)
        assertEquals("at", (result.getOrThrow() as QuickConnectPollResult.Success).tokens.accessToken)
    }

    @Test
    fun `pollQuickConnect returns Pending`() = runTest {
        coEvery { api.pollQuickConnect("tok") } returns QuickConnectPollResult.Pending

        val result = repository.pollQuickConnect("tok")

        assertEquals(Result.success(QuickConnectPollResult.Pending), result)
    }

    @Test
    fun `pollQuickConnect returns Expired`() = runTest {
        coEvery { api.pollQuickConnect("tok") } returns QuickConnectPollResult.Expired

        val result = repository.pollQuickConnect("tok")

        assertEquals(Result.success(QuickConnectPollResult.Expired), result)
    }

    @Test
    fun `pollQuickConnect returns failure when api throws`() = runTest {
        coEvery { api.pollQuickConnect("tok") } throws IllegalStateException("unexpected status: 500")

        val result = repository.pollQuickConnect("tok")

        assertTrue(result.isFailure)
    }

    @Test
    fun `logout returns success and clears tokenStore`() = runTest {
        tokenStore.saveTokens(TokenPair(accessToken = "at", refreshToken = "rt", user = fakeUser))
        coEvery { api.logout("rt") } returns Unit

        val result = repository.logout("rt")

        assertTrue(result.isSuccess)
        assertNull(tokenStore.accessToken())
        assertNull(tokenStore.refreshToken())
    }

    @Test
    fun `logout returns failure and does not clear tokenStore`() = runTest {
        tokenStore.saveTokens(TokenPair(accessToken = "at", refreshToken = "rt", user = fakeUser))
        coEvery { api.logout("rt") } throws IllegalStateException("401")

        val result = repository.logout("rt")

        assertTrue(result.isFailure)
        assertEquals("at", tokenStore.accessToken())
    }

    @Test
    fun `logoutAll returns success and clears tokenStore`() = runTest {
        tokenStore.saveTokens(TokenPair(accessToken = "at", refreshToken = "rt", user = fakeUser))
        coEvery { api.logoutAll() } returns Unit

        val result = repository.logoutAll()

        assertTrue(result.isSuccess)
        assertNull(tokenStore.accessToken())
    }

    @Test
    fun `logoutAll returns failure and does not clear tokenStore`() = runTest {
        tokenStore.saveTokens(TokenPair(accessToken = "at", refreshToken = "rt", user = fakeUser))
        coEvery { api.logoutAll() } throws IllegalStateException("401")

        val result = repository.logoutAll()

        assertTrue(result.isFailure)
        assertEquals("at", tokenStore.accessToken())
    }
}
