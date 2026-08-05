package com.stoganet.tv.ui.home

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage

private val POSTER_WIDTH = 120.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(posterUrl: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(POSTER_WIDTH)
            .aspectRatio(POSTER_ASPECT_RATIO)
            .semantics { this.contentDescription = contentDescription },
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview
@Composable
private fun PreviewPosterCard() {
    PosterCard(
        posterUrl = "",
        contentDescription = "The Matrix (1999)",
        onClick = {},
    )
}
