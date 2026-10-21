package com.stoganet.tv.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.stoganet.tv.R
import com.stoganet.tv.ui.IconActionButton
import com.stoganet.tv.ui.detail.CastMemberUiState
import com.stoganet.tv.ui.detail.CastSection
import com.stoganet.tv.ui.detail.DetailBackdrop
import com.stoganet.tv.ui.detail.DetailScrim
import com.stoganet.tv.ui.rememberInitialFocusRequester
import kotlinx.collections.immutable.persistentListOf

private const val PANEL_WIDTH_FRACTION = 0.52f
private const val OVERVIEW_MAX_LINES = 5
private const val STILL_WIDTH_FRACTION = 0.7f
private const val STILL_ASPECT_RATIO = 16f / 9f
private const val PROGRESS_BAR_HEIGHT_DP = 4

private const val FOCUS_PRIMARY = 0
private const val FOCUS_FROM_START = 1
private const val FOCUS_NEXT = 2

@Composable
fun EpisodeScreen(
    state: EpisodeUiState,
    onIntent: (EpisodeIntent) -> Unit,
    onNavigateToPlayer: (id: String, positionMs: Long) -> Unit,
    onNavigateToEpisode: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        EpisodeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        EpisodeUiState.Error -> {
            val retryLabel = stringResource(R.string.action_retry)
            val retryFocusRequester = rememberInitialFocusRequester(enabled = true)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.episode_error_message),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { onIntent(EpisodeIntent.Retry) },
                        modifier = Modifier
                            .focusRequester(retryFocusRequester)
                            .semantics { contentDescription = retryLabel },
                    ) { Text(retryLabel) }
                }
            }
        }

        is EpisodeUiState.Content -> EpisodeContent(
            state = state,
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToEpisode = onNavigateToEpisode,
            modifier = modifier,
        )
    }
}

@Composable
private fun EpisodeContent(
    state: EpisodeUiState.Content,
    onNavigateToPlayer: (id: String, positionMs: Long) -> Unit,
    onNavigateToEpisode: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPlay = state.isPlayable
    val showFromStart = showPlay && state.positionMs > 0
    val showNext = state.nextEpisodeId != null

    val primaryRequester = remember { FocusRequester() }
    val fromStartRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }

    // Survives the trip to the player so focus returns to the button that was pressed
    var lastPressed by rememberSaveable { mutableIntStateOf(FOCUS_PRIMARY) }

    LaunchedEffect(Unit) {
        val target = when {
            lastPressed == FOCUS_FROM_START && showFromStart -> fromStartRequester
            lastPressed == FOCUS_NEXT && showNext -> nextRequester
            showPlay -> primaryRequester
            showNext -> nextRequester
            else -> null
        }
        target?.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(backdropUrl = state.backdropUrl)
        DetailScrim()
        // Not a LazyColumn: a focus request needs the buttons composed, however tall the text is
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(PANEL_WIDTH_FRACTION)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EpisodeHeader(state = state)
            EpisodeStill(state = state)
            EpisodeActions(
                state = state,
                showPlay = showPlay,
                showFromStart = showFromStart,
                primaryRequester = primaryRequester,
                fromStartRequester = fromStartRequester,
                nextRequester = nextRequester,
                onPlay = {
                    lastPressed = FOCUS_PRIMARY
                    onNavigateToPlayer(state.playId, state.positionMs)
                },
                onPlayFromStart = {
                    lastPressed = FOCUS_FROM_START
                    onNavigateToPlayer(state.playId, 0L)
                },
                onNext = { id ->
                    lastPressed = FOCUS_NEXT
                    onNavigateToEpisode(id)
                },
            )
            if (state.cast.isNotEmpty()) {
                CastSection(cast = state.cast)
            }
        }
    }
}

@Composable
private fun EpisodeHeader(state: EpisodeUiState.Content) {
    Column {
        if (state.seriesTitle != null) {
            Text(
                text = state.seriesTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
        }
        if (state.seasonNumber != null && state.episodeNumber != null) {
            Text(
                text = stringResource(R.string.episode_season_episode, state.seasonNumber, state.episodeNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))

        val runtime = state.runtime.ifEmpty { null }
        val meta = if (runtime != null && state.airDate != null) {
            stringResource(R.string.episode_meta, runtime, state.airDate)
        } else {
            runtime ?: state.airDate
        }
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
        }
        if (state.overview.isNotBlank()) {
            Text(
                text = state.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = OVERVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EpisodeStill(state: EpisodeUiState.Content) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth(STILL_WIDTH_FRACTION)
                .aspectRatio(STILL_ASPECT_RATIO)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // A failed load draws nothing, so the surface box above stays visible
            if (state.stillUrl != null) {
                AsyncImage(
                    model = state.stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.played) {
                Text(
                    text = stringResource(R.string.episode_watched_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (state.positionMs > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(PROGRESS_BAR_HEIGHT_DP.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.progressFraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Suppress("LongParameterList")
@Composable
private fun EpisodeActions(
    state: EpisodeUiState.Content,
    showPlay: Boolean,
    showFromStart: Boolean,
    primaryRequester: FocusRequester,
    fromStartRequester: FocusRequester,
    nextRequester: FocusRequester,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onNext: (id: String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showPlay) {
            val isResume = state.positionMs > 0
            val desc = stringResource(
                if (isResume) R.string.detail_resume_content_description else R.string.detail_play_content_description,
                state.title,
            )
            IconActionButton(
                iconRes = R.drawable.ic_play,
                label = desc,
                onClick = onPlay,
                modifier = Modifier.focusRequester(primaryRequester),
            )
        }
        if (showFromStart) {
            IconActionButton(
                iconRes = R.drawable.ic_replay,
                label = stringResource(R.string.episode_play_from_start_content_description, state.title),
                onClick = onPlayFromStart,
                modifier = Modifier.focusRequester(fromStartRequester),
            )
        }
        if (state.nextEpisodeId != null) {
            IconActionButton(
                iconRes = R.drawable.ic_skip_next,
                label = stringResource(R.string.episode_next_content_description),
                onClick = { onNext(state.nextEpisodeId) },
                modifier = Modifier.focusRequester(nextRequester),
            )
        }
    }
}

private fun previewContent() = EpisodeUiState.Content(
    playId = "ep3",
    title = "Test Episode",
    runtime = "48m",
    overview = "An episode overview.",
    cast = persistentListOf(CastMemberUiState("Test Actor", "Actor")),
    isPlayable = true,
    seriesTitle = "Test Show",
    seasonNumber = 1,
    episodeNumber = 3,
    airDate = "Feb 10, 2008",
    nextEpisodeId = "ep4",
)

@Composable
private fun PreviewScreen(state: EpisodeUiState) {
    EpisodeScreen(state = state, onIntent = {}, onNavigateToPlayer = { _, _ -> }, onNavigateToEpisode = {})
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewLoading() = PreviewScreen(EpisodeUiState.Loading)

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewError() = PreviewScreen(EpisodeUiState.Error)

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewResume() = PreviewScreen(previewContent().copy(positionMs = 1_200_000L, progressFraction = 0.42f))

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewNotStarted() = PreviewScreen(previewContent())

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewWatched() = PreviewScreen(previewContent().copy(played = true))

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewNotPlayable() = PreviewScreen(previewContent().copy(isPlayable = false))

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewNoSeriesInfo() = PreviewScreen(
    previewContent().copy(seriesTitle = null, seasonNumber = null, episodeNumber = null, nextEpisodeId = null),
)
