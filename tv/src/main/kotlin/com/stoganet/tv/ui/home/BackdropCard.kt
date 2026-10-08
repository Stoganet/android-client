package com.stoganet.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

val BACKDROP_CARD_WIDTH = 140.dp
const val BACKDROP_CARD_ASPECT_RATIO = 16f / 9f

private const val TITLE_MAX_LINES = 2
private val TITLE_SCRIM = Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))

@Composable
fun BackdropCard(
    title: String,
    imageUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHasTitle: Boolean = false,
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier
            .width(BACKDROP_CARD_WIDTH)
            .aspectRatio(BACKDROP_CARD_ASPECT_RATIO)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUrl == null || imageFailed) {
                TitleOnly(title)
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!imageHasTitle) TitleOverlay(title)
            }
        }
    }
}

@Composable
private fun TitleOnly(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TitleOverlay(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TITLE_SCRIM),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
private val previewImageHandler = AsyncImagePreviewHandler { ColorImage(Color(0xFF3A4A5C).toArgb()) }

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun PreviewBackdropCardWithTitle() {
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewImageHandler) {
        BackdropCard(
            title = "Test Movie",
            imageUrl = "https://img/thumb",
            contentDescription = "Test Movie (2001)",
            onClick = {},
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun PreviewBackdropCardImageHasTitle() {
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewImageHandler) {
        BackdropCard(
            title = "Test Movie",
            imageUrl = "https://img/thumb",
            contentDescription = "Test Movie (2001)",
            onClick = {},
            imageHasTitle = true,
        )
    }
}

@Preview
@Composable
private fun PreviewBackdropCardNoImage() {
    BackdropCard(
        title = "A Test Show With a Long Title That Wraps",
        imageUrl = null,
        contentDescription = "A Test Show With a Long Title That Wraps (2001)",
        onClick = {},
    )
}
