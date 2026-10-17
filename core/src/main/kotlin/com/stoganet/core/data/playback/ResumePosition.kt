package com.stoganet.core.data.playback

import com.stoganet.core.api.model.WatchProgress

fun WatchProgress?.resumePositionMs(): Long = this?.takeUnless { it.played }?.positionMs ?: 0L
