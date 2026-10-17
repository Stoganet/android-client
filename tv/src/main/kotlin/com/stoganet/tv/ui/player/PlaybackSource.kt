package com.stoganet.tv.ui.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.data.playback.resumePositionMs

internal data class PlaybackSource(
    val streamUrl: String,
    val subtitleTracks: List<SubtitleTrackUi>,
    val title: String,
    val episodeLine: EpisodeLineUi?,
    val nextEpisode: NextEpisodeUi?,
)

internal fun playbackSource(detail: LibraryDetail?): PlaybackSource? {
    val play = detail?.play ?: return null
    return PlaybackSource(
        streamUrl = play.streamUrl,
        subtitleTracks = play.subtitleTracks.map { SubtitleTrackUi(it.index, it.language, it.title, it.isDefault) },
        title = detail.title,
        episodeLine = detail.episode?.let { EpisodeLineUi(it.series.title, it.seasonNumber, it.number) },
        nextEpisode = detail.episode?.next?.let { NextEpisodeUi(it.id, it.progress.resumePositionMs()) },
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
