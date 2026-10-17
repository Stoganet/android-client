package com.stoganet.tv.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stoganet.tv.R
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ControlsOverlay(
    state: PlayerUiState.Ready,
    onIntent: (PlayerIntent) -> Unit,
    onNextEpisode: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val playLabel = stringResource(R.string.player_play_content_description)
    val pauseLabel = stringResource(R.string.player_pause_content_description)
    val subtitlesLabel = stringResource(R.string.player_subtitles_content_description)
    val nextEpisodeLabel = stringResource(R.string.player_next_episode_content_description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 32.dp),
    ) {
        if (state.title != null) {
            PlayerTitle(
                title = state.title,
                episodeLine = state.episodeLine,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        PlayerSeekBar(
            positionMs = state.seekPreviewMs ?: state.positionMs,
            durationMs = state.durationMs,
            onIntent = onIntent,
            modifier = Modifier.focusRequester(focusRequester),
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(
                iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                label = if (state.isPlaying) pauseLabel else playLabel,
                onClick = { onIntent(PlayerIntent.TogglePlayPause) },
            )
            if (state.nextEpisode != null) {
                Spacer(Modifier.width(16.dp))
                PlayerIconButton(
                    iconRes = R.drawable.ic_skip_next,
                    label = nextEpisodeLabel,
                    onClick = onNextEpisode,
                )
            }
            Spacer(Modifier.weight(1f))
            PlayerIconButton(
                iconRes = R.drawable.ic_subtitles,
                label = subtitlesLabel,
                onClick = { onIntent(PlayerIntent.OpenSubtitleMenu) },
            )
        }
    }
}

@Composable
internal fun SubtitleMenuOverlay(
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

@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.player_seek_bar_content_description)

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val trackHeight by animateDpAsState(if (focused) 8.dp else 4.dp, label = "trackHeight")
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val withHours = durationMs.milliseconds >= 1.hours

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onKeyEvent { keyEvent -> handleSeekBarKeyEvent(keyEvent, onIntent) }
                .focusable(interactionSource = interactionSource)
                .semantics { contentDescription = label },
        ) {
            val trackHeightPx = trackHeight.toPx()
            val trackTopLeft = Offset(0f, (size.height - trackHeightPx) / 2)
            val trackSize = Size(size.width, trackHeightPx)
            val cornerRadius = CornerRadius(trackHeightPx / 2)
            val fillEnd = size.width * progress

            drawRoundRect(Color.White.copy(alpha = 0.3f), trackTopLeft, trackSize, cornerRadius)
            clipRect(right = fillEnd) {
                drawRoundRect(Color.White, trackTopLeft, trackSize, cornerRadius)
            }
            if (focused) {
                drawCircle(Color.White, THUMB_SIZE.toPx() / 2, Offset(fillEnd, size.height / 2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            PlaybackTimeText(formatPlaybackTime(positionMs, withHours))
            Spacer(Modifier.weight(1f))
            PlaybackTimeText(formatPlaybackTime(durationMs, withHours))
        }
    }
}

@Composable
private fun PlayerTitle(title: String, episodeLine: EpisodeLineUi?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (episodeLine != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.player_episode_line,
                    episodeLine.seriesTitle,
                    episodeLine.seasonNumber,
                    episodeLine.number,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(IconButtonDefaults.LargeButtonSize)
            .semantics { contentDescription = label },
        colors = IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
        )
    }
}

@Composable
private fun PlaybackTimeText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
}

internal fun handleSeekBarKeyEvent(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    onIntent: (PlayerIntent) -> Unit,
): Boolean = sendOnKeyDown(keyEvent, dpadIntent(keyEvent), onIntent)

internal fun formatPlaybackTime(ms: Long, withHours: Boolean): String =
    ms.coerceAtLeast(0).milliseconds.toComponents { hours, minutes, seconds, _ ->
        if (withHours) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", hours * MINUTES_PER_HOUR + minutes, seconds)
        }
    }

private val THUMB_SIZE = 16.dp
private const val MINUTES_PER_HOUR = 60
