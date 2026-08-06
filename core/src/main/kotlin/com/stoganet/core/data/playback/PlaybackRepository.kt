package com.stoganet.core.data.playback

import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure

private const val TAG = "PlaybackRepository"

class PlaybackRepository(private val api: StoganetApi) {
    suspend fun reportProgress(itemId: String, positionMs: Long, played: Boolean): Result<Unit> =
        runCatching { api.reportProgress(itemId, positionMs, played) }.logOnFailure(TAG)
}
