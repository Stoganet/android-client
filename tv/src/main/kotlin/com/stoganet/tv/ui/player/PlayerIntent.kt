package com.stoganet.tv.ui.player

sealed interface PlayerIntent {
    data object Exit : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object SeekBackward : PlayerIntent
    data object SeekForward : PlayerIntent
    data object ShowControls : PlayerIntent
    data object OpenSubtitleMenu : PlayerIntent
    data object CloseSubtitleMenu : PlayerIntent
    data class SelectSubtitleTrack(val index: Int?) : PlayerIntent
}
