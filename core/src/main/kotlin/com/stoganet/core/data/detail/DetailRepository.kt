package com.stoganet.core.data.detail

import com.stoganet.core.api.model.Episode
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.data.playback.PlaybackRepository
import com.stoganet.core.util.logOnFailure
import com.stoganet.core.util.suspendRunCatching
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

private const val TAG = "DetailRepository"

@OptIn(ExperimentalCoroutinesApi::class)
class DetailRepository(private val api: StoganetApi, private val playbackRepository: PlaybackRepository) {
    suspend fun getDetail(id: String): Result<LibraryDetail> =
        suspendRunCatching { api.getDetail(id) }.logOnFailure(TAG)

    suspend fun getEpisodes(id: String, seasonNumber: Int): Result<List<Episode>> =
        suspendRunCatching { api.getEpisodes(id, seasonNumber) }.logOnFailure(TAG)

    fun detail(id: String): Flow<Result<LibraryDetail>> = refreshedAfterPlayback { getDetail(id) }

    fun episodes(id: String, seasonNumber: Int): Flow<Result<List<Episode>>> =
        refreshedAfterPlayback { getEpisodes(id, seasonNumber) }

    private fun <T> refreshedAfterPlayback(fetch: suspend () -> T): Flow<T> = playbackRepository.progressSaved
        .onStart { emit(Unit) }
        .mapLatest { fetch() }
}
