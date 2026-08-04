package com.stoganet.tv.data.detail

import com.stoganet.tv.api.model.Episode
import com.stoganet.tv.api.model.LibraryDetail
import com.stoganet.tv.data.net.StoganetApi

class DetailRepository(private val api: StoganetApi) {
    suspend fun getDetail(id: String): Result<LibraryDetail> = runCatching { api.getDetail(id) }

    suspend fun getEpisodes(id: String, seasonNumber: Int): Result<List<Episode>> =
        runCatching { api.getEpisodes(id, seasonNumber) }
}
