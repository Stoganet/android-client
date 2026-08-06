package com.stoganet.core.data.playback

import com.stoganet.core.data.net.StoganetApi

class PlaybackRepository(private val api: StoganetApi) {
    suspend fun reportProgress(itemId: String, positionMs: Long, played: Boolean): Result<Unit> =
        runCatching { api.reportProgress(itemId, positionMs, played) }
}
