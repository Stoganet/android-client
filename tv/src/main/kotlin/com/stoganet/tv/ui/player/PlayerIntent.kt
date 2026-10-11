package com.stoganet.tv.ui.player

sealed interface PlayerIntent {
    data object Exit : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data class SeekBackward(val repeatCount: Int = 0) : PlayerIntent
    data class SeekForward(val repeatCount: Int = 0) : PlayerIntent
    data object ShowControls : PlayerIntent
    data object OpenSubtitleMenu : PlayerIntent
    data object CloseSubtitleMenu : PlayerIntent
    data class SelectSubtitleTrack(val index: Int?) : PlayerIntent
}
