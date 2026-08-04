package com.stoganet.tv.data.net

import com.stoganet.tv.api.model.Episode
import com.stoganet.tv.api.model.EpisodeListResponse
import com.stoganet.tv.api.model.HomeResponse
import com.stoganet.tv.api.model.LibraryDetail
import com.stoganet.tv.api.model.LibraryListResponse
import com.stoganet.tv.api.model.LoginRequest
import com.stoganet.tv.api.model.MediaType
import com.stoganet.tv.api.model.QuickConnectPollRequest
import com.stoganet.tv.api.model.QuickConnectStartResponse
import com.stoganet.tv.api.model.RefreshRequest
import com.stoganet.tv.api.model.TokenPair
import com.stoganet.tv.data.auth.QuickConnectPollResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class StoganetApi(private val client: HttpClient, private val baseUrl: String = BASE_URL) {

    suspend fun login(username: String, password: String, deviceLabel: String?): TokenPair {
        val response = client.post("${baseUrl}auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password, deviceLabel = deviceLabel))
        }
        check(response.status.isSuccess()) { "login failed: ${response.status.value}" }
        return response.body()
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
}
