package com.stoganet.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(state: PlayerUiState, onBack: () -> Unit, modifier: Modifier = Modifier, player: ExoPlayer? = null) {
    BackHandler(onBack = onBack)

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

        PlayerUiState.Ready -> {
            val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
            AndroidView(
                modifier = modifier
                    .fillMaxSize()
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        playerViewRef.value?.dispatchKeyEvent(keyEvent.nativeKeyEvent) ?: false
                    },
                factory = { context ->
                    PlayerView(context).also { pv ->
                        playerViewRef.value = pv
                        pv.player = player
                        pv.keepScreenOn = true
                    }
                },
                update = { pv -> pv.player = player },
            )
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
