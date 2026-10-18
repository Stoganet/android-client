package com.stoganet.core.data.playback

import com.stoganet.core.data.net.StoganetApi
import com.stoganet.core.util.logOnFailure
import com.stoganet.core.util.suspendRunCatching
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlaybackRepository"

data class ProgressUpdate(val itemId: String, val positionMs: Long, val played: Boolean)

class PlaybackRepository(private val api: StoganetApi) {

    private val _lastProgress = MutableStateFlow<ProgressUpdate?>(null)
    val lastProgress: StateFlow<ProgressUpdate?> = _lastProgress.asStateFlow()

    private val _progressSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val progressSaved: SharedFlow<Unit> = _progressSaved.asSharedFlow()

    suspend fun reportProgress(itemId: String, positionMs: Long, played: Boolean): Result<Unit> = suspendRunCatching {
        api.reportProgress(itemId, positionMs, played)
        _lastProgress.value = ProgressUpdate(itemId, positionMs, played)
        _progressSaved.emit(Unit)
    }.logOnFailure(TAG)
}
