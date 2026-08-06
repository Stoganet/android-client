package com.stoganet.core.data.detail

import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure

private const val TAG = "DetailRepository"

class DetailRepository(private val api: StoganetApi) {
    suspend fun getDetail(id: String): Result<LibraryDetail> = runCatching { api.getDetail(id) }.logOnFailure(TAG)

    suspend fun getEpisodes(id: String, seasonNumber: Int): Result<List<Episode>> =
        runCatching { api.getEpisodes(id, seasonNumber) }.logOnFailure(TAG)
}
