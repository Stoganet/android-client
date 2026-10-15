package com.stoganet.tv.ui.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.stoganet.core.api.model.LibraryDetail

// What the player needs to start, worked out from detail and route args
internal data class PlaybackSource(
    val streamUrl: String,
    val subtitleTracks: List<SubtitleTrackUi>,
    val title: String?,
    val episodeLine: EpisodeLineUi?,
)

// The route's stream wins so a failed detail fetch still plays, just without title or subtitles
internal fun playbackSource(detail: LibraryDetail?, routeStreamUrl: String?): PlaybackSource? {
    val url = routeStreamUrl ?: detail?.play?.streamUrl ?: return null
    return PlaybackSource(
        streamUrl = url,
        subtitleTracks = detail?.play?.subtitleTracks.orEmpty()
            .map { SubtitleTrackUi(it.index, it.language, it.title, it.isDefault) },
        title = detail?.title,
        episodeLine = detail?.episode?.let { EpisodeLineUi(it.series.title, it.seasonNumber, it.number) },
    )
}

internal fun initialSubtitle(tracks: List<SubtitleTrackUi>, preferredLanguage: String?): SubtitleTrackUi? =
    tracks.firstOrNull { it.language == preferredLanguage } ?: tracks.firstOrNull { it.isDefault }

internal fun PlaybackSource.toMediaItem(): MediaItem {
    val subtitleConfigs = subtitleTracks.map { track ->
        MediaItem.SubtitleConfiguration.Builder("$streamUrl/subtitles/${track.index}".toUri())
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(track.language)
            .setLabel(track.title)
            .setId(track.index.toString())
            .build()
    }
    return MediaItem.Builder().setUri(streamUrl).setSubtitleConfigurations(subtitleConfigs).build()
}
