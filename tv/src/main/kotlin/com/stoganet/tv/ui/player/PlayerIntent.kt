package com.stoganet.tv.ui.player

sealed interface PlayerIntent {
    data object Exit : PlayerIntent
}
