package com.stoganet.tv.data.auth

import com.stoganet.tv.api.model.TokenPair
import com.stoganet.tv.api.model.User
import com.stoganet.tv.data.net.configurePlugins
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthPluginTest {

    private val fakeUser = User(id = "u1", email = "a@b.com", displayName = "Test")

    private fun tokenPairJson(access: String, refresh: String) =
        """{"access_token":"$access","refresh_token":"$refresh","user":{"id":"u1","email":"a@b.com","display_name":"Test"}}"""

    private fun buildTestClient(tokenStore: TokenStore, engine: MockEngine): HttpClient =
        HttpClient(engine) { configurePlugins(tokenStore, baseUrl = "http://test.local/") }

    @Test
    fun `bearer token injected when token present`() = runTest {
        val tokenStore = TokenStore(FakeDataStore())
        tokenStore.saveTokens(TokenPair(accessToken = "access-abc", refreshToken = "refresh-xyz", user = fakeUser))
        val engine = MockEngine { respond("OK", HttpStatusCode.OK) }
        val client = buildTestClient(tokenStore, engine)

        client.use { it.get("http://test.local/test") }

        assertEquals("Bearer access-abc", engine.requestHistory.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `no Authorization header when no token stored`() = runTest {
        val tokenStore = TokenStore(FakeDataStore())
        val engine = MockEngine { respond("OK", HttpStatusCode.OK) }
        val client = buildTestClient(tokenStore, engine)

        client.use { it.get("http://test.local/test") }

        assertNull(engine.requestHistory.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `401 triggers refresh and retries with new token`() = runTest {
        val tokenStore = TokenStore(FakeDataStore())
        tokenStore.saveTokens(TokenPair(accessToken = "old-access", refreshToken = "old-refresh", user = fakeUser))
        var callCount = 0
        val engine = MockEngine { request ->
            callCount++
            when {
                callCount == 1 -> respond(
                    "",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.WWWAuthenticate, "Bearer realm=\"test\""),
                )

                request.url.encodedPath.contains("refresh") -> respond(
                    tokenPairJson("new-access", "new-refresh"),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )

                else -> respond("OK", HttpStatusCode.OK)
            }
        }
        val client = buildTestClient(tokenStore, engine)

        client.use { it.get("http://test.local/protected") }

        assertEquals("Bearer new-access", engine.requestHistory.last().headers[HttpHeaders.Authorization])
        assertEquals("new-access", tokenStore.accessToken())
        assertEquals("new-refresh", tokenStore.refreshToken())
    }

    @Test
    fun `failed refresh clears token store`() = runTest {
        val tokenStore = TokenStore(FakeDataStore())
        tokenStore.saveTokens(TokenPair(accessToken = "old-access", refreshToken = "old-refresh", user = fakeUser))
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            when {
                callCount == 1 -> respond(
                    "",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.WWWAuthenticate, "Bearer realm=\"test\""),
                )

                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }
        val client = buildTestClient(tokenStore, engine)

        client.use { it.get("http://test.local/protected") }

        assertNull(tokenStore.accessToken())
        assertNull(tokenStore.refreshToken())
    }
}
