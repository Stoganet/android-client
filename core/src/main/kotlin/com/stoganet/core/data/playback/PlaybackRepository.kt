package com.stoganet.core.data.playback

import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure
import com.stoganet.core.util.suspendRunCatching
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TAG = "PlaybackRepository"

class PlaybackRepository(private val api: StoganetApi) {

    private val _progressSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val progressSaved: SharedFlow<Unit> = _progressSaved.asSharedFlow()

    suspend fun reportProgress(itemId: String, positionMs: Long, played: Boolean): Result<Unit> = suspendRunCatching {
        api.reportProgress(itemId, positionMs, played)
        _progressSaved.emit(Unit)
    }.logOnFailure(TAG)
}
