package com.stoganet.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal const val HOME_HERO_TITLE_TAG = "home_hero_title"

private const val HERO_IMAGE_DELAY_MS = 300L
private const val HERO_CROSSFADE_MS = 400
private const val HERO_TITLE_MAX_LINES = 2
private const val HERO_OVERVIEW_MAX_LINES = 3
private val HERO_TEXT_MAX_WIDTH = 480.dp
private const val SIDE_SCRIM_MID_STOP = 0.35f
private const val SIDE_SCRIM_MID_ALPHA = 0.7f
private const val SIDE_SCRIM_END_STOP = 0.7f
private const val BOTTOM_SCRIM_START_STOP = 0.35f
private const val BOTTOM_SCRIM_END_STOP = 0.65f

@Composable
fun HomeHeroBackdrop(backdropUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    var shownUrl by remember { mutableStateOf(backdropUrl) }
    LaunchedEffect(backdropUrl) {
        delay(HERO_IMAGE_DELAY_MS.milliseconds)
        if (backdropUrl != null) context.imageLoader.execute(heroImageRequest(context, backdropUrl))
        shownUrl = backdropUrl
    }
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(targetState = shownUrl, animationSpec = tween(HERO_CROSSFADE_MS), label = "heroBackdrop") { url ->
            if (url != null) {
                AsyncImage(
                    model = remember(url) { heroImageRequest(context, url) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background,
                        SIDE_SCRIM_MID_STOP to background.copy(alpha = SIDE_SCRIM_MID_ALPHA),
                        SIDE_SCRIM_END_STOP to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        BOTTOM_SCRIM_START_STOP to Color.Transparent,
                        BOTTOM_SCRIM_END_STOP to background,
                    ),
                ),
        )
    }
}

@Composable
fun HomeHeroText(item: HomeItemUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.widthIn(max = HERO_TEXT_MAX_WIDTH)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = HERO_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(HOME_HERO_TITLE_TAG),
        )
        if (item.year > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.year.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = item.overview,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = HERO_OVERVIEW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Same request for preload and display so the display hits the memory cache
private fun heroImageRequest(context: PlatformContext, url: String) =
    ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).build()

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun PreviewHomeHero() {
    Box(modifier = Modifier.fillMaxSize()) {
        HomeHeroBackdrop(backdropUrl = null)
        HomeHeroText(
            item = HomeItemUiState(
                id = "1",
                title = "Test Movie",
                year = 2001,
                overview = "A test overview that runs long enough to wrap onto a second and third line " +
                    "so the clamp to three lines shows up in the preview as it would on a real item.",
                contentDescription = "Test Movie (2001)",
            ),
        )
    }
}
