package com.stoganet.core.data.net

import android.util.Log
import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.EpisodeListResponse
import com.stoganet.core.api.model.HomeResponse
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.LibraryListResponse
import com.stoganet.core.api.model.LoginRequest
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.QuickConnectPollRequest
import com.stoganet.core.api.model.QuickConnectStartResponse
import com.stoganet.core.api.model.RefreshRequest
import com.stoganet.core.api.model.SearchResponse
import com.stoganet.core.api.model.WatchProgress
import com.stoganet.core.data.auth.LoginResult
import com.stoganet.core.data.auth.QuickConnectPollResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException

private const val TAG = "StoganetApi"

// Carries the HTTP status so SearchViewModel can special-case 429 (silent retry-on-next-keystroke)
// vs. other failures (visible error) — the other StoganetApi methods discard the status on failure
// because no caller currently needs to branch on it.
class SearchApiException(val status: HttpStatusCode) : Exception("search failed: ${status.value}")

class StoganetApi(private val client: HttpClient, private val baseUrl: String = BASE_URL) {

    suspend fun login(username: String, password: String, deviceLabel: String?): LoginResult {
        val response = try {
            client.post("${baseUrl}auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = username, password = password, deviceLabel = deviceLabel))
            }
        } catch (e: IOException) {
            Log.w(TAG, e.message, e)
            return LoginResult.NetworkError
        }
        return when (response.status) {
            HttpStatusCode.OK -> LoginResult.Success(response.body())
            HttpStatusCode.Unauthorized, HttpStatusCode.Locked -> LoginResult.InvalidCredentials
            else -> LoginResult.NetworkError
        }
    }

    suspend fun startQuickConnect(): QuickConnectStartResponse {
        val response = client.post("${baseUrl}auth/quick-connect/start") {
            contentType(ContentType.Application.Json)
        }
        check(response.status.isSuccess()) { "startQuickConnect failed: ${response.status.value}" }
        return response.body()
    }

    suspend fun pollQuickConnect(pollToken: String): QuickConnectPollResult {
        val response = client.post("${baseUrl}auth/quick-connect/poll") {
            contentType(ContentType.Application.Json)
            setBody(QuickConnectPollRequest(pollToken = pollToken))
        }
        return when (response.status) {
            HttpStatusCode.OK -> QuickConnectPollResult.Success(response.body())
            HttpStatusCode.Accepted -> QuickConnectPollResult.Pending
            HttpStatusCode.Gone -> QuickConnectPollResult.Expired
            else -> error("Unexpected poll status: ${response.status.value}")
        }
    }

    suspend fun logout(refreshToken: String) {
        val response = client.post("${baseUrl}auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken = refreshToken))
        }
        check(response.status.isSuccess()) { "logout failed: ${response.status.value}" }
    }

    suspend fun logoutAll() {
        val response = client.post("${baseUrl}auth/logout/all")
        check(response.status.isSuccess()) { "logoutAll failed: ${response.status.value}" }
    }

    suspend fun getHome(): HomeResponse {
        val response = client.get("${baseUrl}home")
        check(response.status.isSuccess()) { "getHome failed: ${response.status.value}" }
        return response.body()
    }

    suspend fun getLibrary(type: MediaType? = null, cursor: String? = null, limit: Int): LibraryListResponse {
        val response = client.get("${baseUrl}library") {
            type?.let { parameter("type", it.value) }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
        check(response.status.isSuccess()) { "getLibrary failed: ${response.status.value}" }
        return response.body()
    }

    suspend fun getDetail(id: String): LibraryDetail {
        val response = client.get("${baseUrl}library/$id")
        check(response.status.isSuccess()) { "getDetail failed: ${response.status.value}" }
        return response.body()
    }

    suspend fun getEpisodes(id: String, seasonNumber: Int): List<Episode> {
        val response = client.get("${baseUrl}library/$id/seasons/$seasonNumber/episodes")
        check(response.status.isSuccess()) { "getEpisodes failed: ${response.status.value}" }
        return response.body<EpisodeListResponse>().episodes
    }

    suspend fun reportProgress(itemId: String, positionMs: Long, played: Boolean) {
        val response = client.put("${baseUrl}playback/$itemId/progress") {
            contentType(ContentType.Application.Json)
            setBody(WatchProgress(positionMs = positionMs, played = played))
        }
        check(response.status.isSuccess()) { "reportProgress failed: ${response.status.value}" }
    }

    suspend fun search(query: String): SearchResponse {
        val response = client.get("${baseUrl}search") { parameter("q", query) }
        if (!response.status.isSuccess()) throw SearchApiException(response.status)
        return response.body()
    }

    suspend fun requestMovie(id: String) {
        val response = client.post("${baseUrl}library/$id/request")
        check(response.status.isSuccess()) { "requestMovie failed: ${response.status.value}" }
    }
}
