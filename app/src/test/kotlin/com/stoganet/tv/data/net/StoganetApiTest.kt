package com.stoganet.tv.data.net

import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.data.auth.QuickConnectPollResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StoganetApiTest {

    private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun buildApi(engine: MockEngine): StoganetApi {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return StoganetApi(client, "http://test.local/")
    }

    private fun tokenPairJson(access: String = "at", refresh: String = "rt") =
        """{"access_token":"$access","refresh_token":"$refresh","user":{"id":"u1","email":"a@b.com","display_name":"Test"}}"""

    @Test
    fun `login returns TokenPair on 200`() = runTest {
        val engine = MockEngine { respond(tokenPairJson(), HttpStatusCode.OK, jsonHeader) }
        val api = buildApi(engine)

        val result = api.login("user", "pass", null)

        assertEquals("at", result.accessToken)
        assertEquals("rt", result.refreshToken)
    }

    @Test
    fun `login throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.login("user", "pass", null) }
    }

    @Test
    fun `startQuickConnect returns response on 200`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"ABC123","poll_token":"tok-xyz"}""", HttpStatusCode.OK, jsonHeader)
        }
        val api = buildApi(engine)

        val result = api.startQuickConnect()

        assertEquals("ABC123", result.code)
        assertEquals("tok-xyz", result.pollToken)
    }

    @Test
    fun `startQuickConnect throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.startQuickConnect() }
    }

    @Test
    fun `pollQuickConnect returns Success on 200`() = runTest {
        val engine = MockEngine { respond(tokenPairJson(), HttpStatusCode.OK, jsonHeader) }
        val api = buildApi(engine)

        val result = api.pollQuickConnect("tok")

        assertInstanceOf(QuickConnectPollResult.Success::class.java, result)
        assertEquals("at", (result as QuickConnectPollResult.Success).tokens.accessToken)
    }

    @Test
    fun `pollQuickConnect returns Pending on 202`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Accepted) }
        val api = buildApi(engine)

        assertEquals(QuickConnectPollResult.Pending, api.pollQuickConnect("tok"))
    }

    @Test
    fun `pollQuickConnect returns Expired on 410`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Gone) }
        val api = buildApi(engine)

        assertEquals(QuickConnectPollResult.Expired, api.pollQuickConnect("tok"))
    }

    @Test
    fun `pollQuickConnect throws on unexpected status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.pollQuickConnect("tok") }
    }

    @Test
    fun `logout succeeds on 204`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val api = buildApi(engine)

        api.logout("rt")

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `logout throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.logout("rt") }
    }

    @Test
    fun `logoutAll succeeds on 204`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val api = buildApi(engine)

        api.logoutAll()

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `logoutAll throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.logoutAll() }
    }

    @Test
    fun `getHome returns HomeResponse on 200`() = runTest {
        val json = """{"sections":[{"id":"recently_added_movies","items":[],"has_more":false}]}"""
        val engine = MockEngine { respond(json, HttpStatusCode.OK, jsonHeader) }
        val api = buildApi(engine)

        val result = api.getHome()

        assertEquals(1, result.sections.size)
        assertEquals("recently_added_movies", result.sections[0].id)
    }

    @Test
    fun `getHome throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.getHome() }
    }

    @Test
    fun `getLibrary returns LibraryListResponse on 200`() = runTest {
        val json = """{"items":[],"total":0}"""
        val engine = MockEngine { respond(json, HttpStatusCode.OK, jsonHeader) }
        val api = buildApi(engine)

        val result = api.getLibrary(limit = 100)

        assertEquals(0, result.total)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `getLibrary throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.getLibrary(limit = 100) }
    }

    @Test
    fun `getLibrary sends type and limit query params`() = runTest {
        val json = """{"items":[],"total":0}"""
        val engine = MockEngine { request ->
            assertEquals("movie", request.url.parameters["type"])
            assertEquals("50", request.url.parameters["limit"])
            respond(json, HttpStatusCode.OK, jsonHeader)
        }
        val api = buildApi(engine)

        api.getLibrary(type = MediaType.MOVIE, limit = 50)
    }

    @Test
    fun `getLibrary sends cursor query param`() = runTest {
        val json = """{"items":[],"total":0}"""
        val engine = MockEngine { request ->
            assertEquals("tok-123", request.url.parameters["cursor"])
            respond(json, HttpStatusCode.OK, jsonHeader)
        }
        val api = buildApi(engine)

        api.getLibrary(cursor = "tok-123", limit = 100)
    }

    @Test
    fun `getDetail returns LibraryDetail on 200`() = runTest {
        val json = """{"id":"tmdb:movie:603","title":"The Matrix","year":1999,"type":"movie",""" +
            """"poster":"","overview":"A hacker.","state":"playable","genres":[],"runtime":136,"cast":[],"seasons":[]}"""
        val engine = MockEngine { respond(json, HttpStatusCode.OK, jsonHeader) }
        val api = buildApi(engine)

        val result = api.getDetail("tmdb:movie:603")

        assertEquals("tmdb:movie:603", result.id)
        assertEquals("The Matrix", result.title)
        assertEquals(1999, result.year)
    }

    @Test
    fun `getDetail throws on non-success status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val api = buildApi(engine)

        assertThrows<IllegalStateException> { api.getDetail("tmdb:movie:603") }
    }
}
