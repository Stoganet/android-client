package com.stoganet.tv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTvMaterial3Api::class)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReadyPlayerContent(
    state: PlayerUiState.Ready,
    player: ExoPlayer?,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val subtitleMenuFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {
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
            ControlsRow(
                state = state,
                onIntent = onIntent,
                focusRequester = playPauseFocusRequester,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        LaunchedEffect(state.subtitleMenuOpen, state.controlsVisible) {
            when {
                state.subtitleMenuOpen -> subtitleMenuFocusRequester.requestFocus()
                state.controlsVisible -> playPauseFocusRequester.requestFocus()
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
    if (keyEvent.type != KeyEventType.KeyDown) {
        return false
    }
    return when (keyEvent.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            onIntent(PlayerIntent.TogglePlayPause)
            true
        }

        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
            onIntent(PlayerIntent.SeekForward)
            true
        }

        KeyEvent.KEYCODE_MEDIA_REWIND -> {
            onIntent(PlayerIntent.SeekBackward)
            true
        }

        else -> if (!controlsVisible) {
            onIntent(PlayerIntent.ShowControls)
            true
        } else {
            false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlsRow(
    state: PlayerUiState.Ready,
    onIntent: (PlayerIntent) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val playLabel = stringResource(R.string.player_play_content_description)
    val pauseLabel = stringResource(R.string.player_pause_content_description)
    val rewindLabel = stringResource(R.string.player_rewind_content_description)
    val forwardLabel = stringResource(R.string.player_forward_content_description)
    val subtitlesLabel = stringResource(R.string.player_subtitles_content_description)
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(24.dp),
    ) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { onIntent(PlayerIntent.SeekBackward) },
                modifier = Modifier.semantics { contentDescription = rewindLabel },
            ) { Text("-10s") }

            Button(
                onClick = { onIntent(PlayerIntent.TogglePlayPause) },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = if (state.isPlaying) pauseLabel else playLabel },
            ) { Text(if (state.isPlaying) pauseLabel else playLabel) }

            Button(
                onClick = { onIntent(PlayerIntent.SeekForward) },
                modifier = Modifier.semantics { contentDescription = forwardLabel },
            ) { Text("+10s") }

            Button(
                onClick = { onIntent(PlayerIntent.OpenSubtitleMenu) },
                modifier = Modifier.semantics { contentDescription = subtitlesLabel },
            ) { Text(subtitlesLabel) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleMenuOverlay(
    state: PlayerUiState.Ready,
    onIntent: (PlayerIntent) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val menuLabel = stringResource(R.string.player_subtitles_menu_content_description)
    val offLabel = stringResource(R.string.player_subtitles_off)

    Column(
        modifier = modifier
            .width(280.dp)
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
            .semantics { contentDescription = menuLabel },
    ) {
        Button(
            onClick = { onIntent(PlayerIntent.SelectSubtitleTrack(null)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = offLabel },
        ) { Text(if (state.selectedSubtitleIndex == null) "• $offLabel" else offLabel) }

        state.subtitleTracks.forEach { track ->
            val label = track.title
            val selected = state.selectedSubtitleIndex == track.index
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onIntent(PlayerIntent.SelectSubtitleTrack(track.index)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            ) { Text(if (selected) "• $label" else label) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerLoading() {
    PlayerScreen(state = PlayerUiState.Loading, onBack = {})
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewPlayerError() {
    PlayerScreen(state = PlayerUiState.Error, onBack = {})
}

@OptIn(ExperimentalTvMaterial3Api::class)
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

@OptIn(ExperimentalTvMaterial3Api::class)
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

@OptIn(ExperimentalTvMaterial3Api::class)
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

@OptIn(ExperimentalTvMaterial3Api::class)
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
