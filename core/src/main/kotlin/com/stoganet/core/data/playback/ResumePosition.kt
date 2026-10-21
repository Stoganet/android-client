package com.stoganet.core.data.playback

import com.stoganet.core.api.model.WatchProgress

fun WatchProgress?.resumePositionMs(): Long = this?.takeUnless { it.played }?.positionMs ?: 0L

fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
