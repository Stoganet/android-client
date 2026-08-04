package com.stoganet.tv.data.auth

import app.cash.turbine.test
import com.stoganet.tv.api.model.TokenPair
import com.stoganet.tv.api.model.User
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenStoreTest {

    private lateinit var store: TokenStore

    @BeforeEach
    fun setUp() {
        store = TokenStore(FakeDataStore())
    }

    @Test
    fun `isAuthenticated emits false when no token stored`() = runTest {
        store.isAuthenticated.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isAuthenticated emits true after saveTokens`() = runTest {
        val pair = TokenPair(
            accessToken = "access-abc",
            refreshToken = "refresh-xyz",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        store.isAuthenticated.test {
            assertFalse(awaitItem())
            store.saveTokens(pair)
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accessToken returns null when empty`() = runTest {
        assertNull(store.accessToken())
    }

    @Test
    fun `accessToken returns stored value after saveTokens`() = runTest {
        val pair = TokenPair(
            accessToken = "access-abc",
            refreshToken = "refresh-xyz",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        store.saveTokens(pair)
        assertEquals("access-abc", store.accessToken())
    }

    @Test
    fun `refreshToken returns stored value after saveTokens`() = runTest {
        val pair = TokenPair(
            accessToken = "access-abc",
            refreshToken = "refresh-xyz",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        store.saveTokens(pair)
        assertEquals("refresh-xyz", store.refreshToken())
    }

    @Test
    fun `clear wipes stored tokens and isAuthenticated emits false`() = runTest {
        val pair = TokenPair(
            accessToken = "access-abc",
            refreshToken = "refresh-xyz",
            user = User(id = "u1", email = "a@b.com", displayName = "Test"),
        )
        store.saveTokens(pair)
        store.isAuthenticated.test {
            assertTrue(awaitItem())
            store.clear()
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
