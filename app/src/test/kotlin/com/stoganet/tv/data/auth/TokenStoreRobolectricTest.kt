package com.stoganet.tv.data.auth

import androidx.datastore.core.DataStoreFactory
import com.stoganet.tv.api.model.TokenPair
import com.stoganet.tv.api.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenStoreRobolectricTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun buildStore(): TokenStore {
        val file = tmpFolder.newFile("tokens-test.pb")
        val ds = DataStoreFactory.create(
            serializer = TestTokensSerializer(),
            produceFile = { file },
        )
        return TokenStore(ds)
    }

    @Test
    fun `fresh store has no tokens`() = runTest {
        val store = buildStore()
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    @Test
    fun `isAuthenticated false when no tokens`() = runTest {
        val store = buildStore()
        assertEquals(false, store.isAuthenticated.first())
    }

    @Test
    fun `saveTokens persists and retrieves through real serializer`() = runTest {
        val store = buildStore()
        val pair = TokenPair(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            user = User(id = "u1", email = "test@example.com", displayName = "Test User"),
        )

        store.saveTokens(pair)

        assertEquals("access-123", store.accessToken())
        assertEquals("refresh-456", store.refreshToken())
        val current = store.current()
        assertEquals("u1", current.userId)
        assertEquals("Test User", current.displayName)
    }

    @Test
    fun `isAuthenticated true after saveTokens`() = runTest {
        val store = buildStore()
        val pair = TokenPair(
            accessToken = "at",
            refreshToken = "rt",
            user = User(id = "u1", email = "a@b.com", displayName = "A"),
        )

        store.saveTokens(pair)

        assertTrue(store.isAuthenticated.first())
    }

    @Test
    fun `clear wipes tokens`() = runTest {
        val store = buildStore()
        store.saveTokens(
            TokenPair(
                accessToken = "at",
                refreshToken = "rt",
                user = User(id = "u1", email = "a@b.com", displayName = "A"),
            ),
        )

        store.clear()

        assertNull(store.accessToken())
        assertNull(store.refreshToken())
        val cleared = store.current()
        assertEquals("", cleared.userId)
        assertEquals("", cleared.displayName)
    }
}
