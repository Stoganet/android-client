package com.stoganet.tv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R
import kotlinx.collections.immutable.persistentListOf

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    player: ExoPlayer? = null,
    onIntent: (PlayerIntent) -> Unit = {},
) {
    BackHandler(
        onBack = {
            if (state is PlayerUiState.Ready && state.subtitleMenuOpen) {
                onIntent(PlayerIntent.CloseSubtitleMenu)
            } else {
                onBack()
            }
        },
    )

    when (state) {
        PlayerUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        PlayerUiState.Error -> {
            val backLabel = stringResource(R.string.player_back_content_description)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.player_error_message),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) { Text(backLabel) }
                }
            }
        }

        is PlayerUiState.Ready -> ReadyPlayerContent(
            state = state,
            player = player,
            onIntent = onIntent,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReadyPlayerContent(
    state: PlayerUiState.Ready,
    player: ExoPlayer?,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val subtitleMenuFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(surfaceFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent -> handlePlayerKeyEvent(keyEvent, state.controlsVisible, onIntent) },
            factory = { context ->
                PlayerView(context).also { pv ->
                    pv.useController = false
                    pv.player = player
                    pv.keepScreenOn = true
                }
            },
            update = { pv -> pv.player = player },
        )

        if (state.subtitleMenuOpen) {
            SubtitleMenuOverlay(
                state = state,
                onIntent = onIntent,
                focusRequester = subtitleMenuFocusRequester,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        } else if (state.controlsVisible) {
            ControlsOverlay(
                state = state,
                onIntent = onIntent,
                focusRequester = controlsFocusRequester,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        LaunchedEffect(state.subtitleMenuOpen, state.controlsVisible) {
            when {
                state.subtitleMenuOpen -> subtitleMenuFocusRequester.requestFocus()
                state.controlsVisible -> controlsFocusRequester.requestFocus()
                else -> surfaceFocusRequester.requestFocus()
            }
        }
    }
}

internal fun handlePlayerKeyEvent(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    controlsVisible: Boolean,
    onIntent: (PlayerIntent) -> Unit,
): Boolean {
    val intent = mediaKeyIntent(keyEvent) ?: when {
        controlsVisible -> null
        else -> dpadIntent(keyEvent) ?: PlayerIntent.ShowControls
    }
    return sendOnKeyDown(keyEvent, intent, onIntent)
}

// Shared with the seek bar so the D-pad maps to the same intents on the video and the controls
internal fun dpadIntent(keyEvent: androidx.compose.ui.input.key.KeyEvent): PlayerIntent? {
    val repeatCount = keyEvent.nativeKeyEvent.repeatCount
    return when (keyEvent.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> PlayerIntent.TogglePlayPause

        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerIntent.SeekBackward(repeatCount)

        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerIntent.SeekForward(repeatCount)

        else -> null
    }
}

internal fun sendOnKeyDown(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    intent: PlayerIntent?,
    onIntent: (PlayerIntent) -> Unit,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown || intent == null) {
        return false
    }
    // Holding OK or play/pause must not toggle again on every key repeat
    if (intent == PlayerIntent.TogglePlayPause && keyEvent.nativeKeyEvent.repeatCount > 0) {
        return true
    }
    onIntent(intent)
    return true
}

private fun mediaKeyIntent(keyEvent: androidx.compose.ui.input.key.KeyEvent): PlayerIntent? {
    val repeatCount = keyEvent.nativeKeyEvent.repeatCount
    return when (keyEvent.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        -> PlayerIntent.TogglePlayPause

        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerIntent.SeekForward(repeatCount)

        KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerIntent.SeekBackward(repeatCount)

        else -> null
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerLoading() {
    PlayerScreen(state = PlayerUiState.Loading, onBack = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerError() {
    PlayerScreen(state = PlayerUiState.Error, onBack = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerReadyPlaying() {
    PlayerScreen(
        state = PlayerUiState.Ready(
            isPlaying = true,
            positionMs = 30_000L,
            durationMs = 120_000L,
            controlsVisible = true,
        ),
        onBack = {},
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerReadyPaused() {
    PlayerScreen(
        state = PlayerUiState.Ready(
            isPlaying = false,
            positionMs = 60_000L,
            durationMs = 120_000L,
            controlsVisible = true,
        ),
        onBack = {},
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerSeeking() {
    PlayerScreen(
        state = PlayerUiState.Ready(
            isPlaying = true,
            positionMs = 1_800_000L,
            durationMs = 7_200_000L,
            controlsVisible = true,
            seekPreviewMs = 2_400_000L,
        ),
        onBack = {},
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerSubtitleMenuOpen() {
    PlayerScreen(
        state = PlayerUiState.Ready(
            isPlaying = true,
            positionMs = 30_000L,
            durationMs = 120_000L,
            controlsVisible = true,
            subtitleTracks = persistentListOf(
                SubtitleTrackUi(index = 2, language = "eng", title = "English", isDefault = true),
                SubtitleTrackUi(index = 3, language = "fin", title = "Finnish", isDefault = false),
            ),
            selectedSubtitleIndex = 2,
            subtitleMenuOpen = true,
        ),
        onBack = {},
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerControlsHidden() {
    PlayerScreen(
        state = PlayerUiState.Ready(
            isPlaying = true,
            positionMs = 30_000L,
            durationMs = 120_000L,
            controlsVisible = false,
        ),
        onBack = {},
    )
}
