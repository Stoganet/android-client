package com.stoganet.tv.ui.player

import com.stoganet.core.api.model.EpisodeInfo
import com.stoganet.core.api.model.LibraryDetail
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.core.api.model.NextEpisode
import com.stoganet.core.api.model.PlayInfo
import com.stoganet.core.api.model.SeriesRef
import com.stoganet.core.api.model.SubtitleTrack
import com.stoganet.core.api.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceTest {

    private fun detail(
        play: PlayInfo? = PlayInfo(streamUrl = "https://api.stoganet.com/stream/detail", subtitleTracks = emptyList()),
        episode: EpisodeInfo? = null,
    ) = LibraryDetail(
        id = "jf:item1",
        title = "Test Title",
        year = 2020,
        type = if (episode != null) MediaType.TV else MediaType.MOVIE,
        poster = "https://img/poster",
        thumbHasLogo = false,
        overview = "",
        state = MediaState.PLAYABLE,
        genres = emptyList(),
        runtime = 0,
        cast = emptyList(),
        seasons = emptyList(),
        play = play,
        episode = episode,
    )

    private fun episode(next: NextEpisode? = null) = EpisodeInfo(
        series = SeriesRef(id = "jf:show1", title = "Test Show"),
        seasonNumber = 1,
        number = 3,
        next = next,
    )

    private fun next(progress: WatchProgress? = null) =
        NextEpisode(id = "jf:ep4", seasonNumber = 1, number = 4, title = "Next Title", progress = progress)

    private fun track(index: Int, language: String, isDefault: Boolean = false) =
        SubtitleTrackUi(index = index, language = language, title = language, isDefault = isDefault)

    @Test
    fun routeStreamUrl_winsOverDetail() {
        val source = playbackSource(detail(), routeStreamUrl = "https://api.stoganet.com/stream/route")

        assertEquals("https://api.stoganet.com/stream/route", source?.streamUrl)
    }

    @Test
    fun noRouteStreamUrl_usesDetail() {
        val source = playbackSource(detail(), routeStreamUrl = null)

        assertEquals("https://api.stoganet.com/stream/detail", source?.streamUrl)
    }

    @Test
    fun noStreamUrlAnywhere_returnsNull() {
        assertNull(playbackSource(detail(play = null), routeStreamUrl = null))
        assertNull(playbackSource(null, routeStreamUrl = null))
    }

    @Test
    fun failedDetail_playsRouteStreamWithoutTitleOrTracks() {
        val source = playbackSource(null, routeStreamUrl = "https://api.stoganet.com/stream/route")

        assertEquals(PlaybackSource("https://api.stoganet.com/stream/route", emptyList(), null, null, null), source)
    }

    @Test
    fun movie_hasTitleAndTracksWithoutEpisodeLine() {
        val tracks = listOf(
            SubtitleTrack(
                index = 2,
                language = "eng",
                title = "English",
                codec = "subrip",
                isDefault = true,
                isForced = false,
                isExternal = false,
            ),
        )
        val play = PlayInfo(streamUrl = "https://api.stoganet.com/stream/detail", subtitleTracks = tracks)

        val source = playbackSource(detail(play = play), routeStreamUrl = null)

        assertEquals("Test Title", source?.title)
        assertNull(source?.episodeLine)
        assertEquals(listOf(SubtitleTrackUi(2, "eng", "English", true)), source?.subtitleTracks)
    }

    @Test
    fun episode_hasEpisodeLine() {
        val source = playbackSource(detail(episode = episode()), routeStreamUrl = null)

        assertEquals("Test Title", source?.title)
        assertEquals(EpisodeLineUi(seriesTitle = "Test Show", seasonNumber = 1, number = 3), source?.episodeLine)
    }

    @Test
    fun nextEpisode_partlyWatched_resumesAtItsProgress() {
        val episode = episode(next(WatchProgress(positionMs = 120_000L, played = false)))

        val source = playbackSource(detail(episode = episode), routeStreamUrl = null)

        assertEquals(NextEpisodeUi(id = "jf:ep4", positionMs = 120_000L), source?.nextEpisode)
    }

    @Test
    fun nextEpisode_alreadyWatched_startsFromBeginning() {
        val episode = episode(next(WatchProgress(positionMs = 120_000L, played = true)))

        val source = playbackSource(detail(episode = episode), routeStreamUrl = null)

        assertEquals(NextEpisodeUi(id = "jf:ep4", positionMs = 0L), source?.nextEpisode)
    }

    @Test
    fun nextEpisode_neverWatched_startsFromBeginning() {
        val source = playbackSource(detail(episode = episode(next())), routeStreamUrl = null)

        assertEquals(NextEpisodeUi(id = "jf:ep4", positionMs = 0L), source?.nextEpisode)
    }

    @Test
    fun lastEpisodeOrMovie_hasNoNextEpisode() {
        assertNull(playbackSource(detail(episode = episode()), routeStreamUrl = null)?.nextEpisode)
        assertNull(playbackSource(detail(), routeStreamUrl = null)?.nextEpisode)
    }

    @Test
    fun initialSubtitle_prefersSavedLanguage() {
        val tracks = listOf(track(1, "eng", isDefault = true), track(2, "fin"))

        assertEquals(2, initialSubtitle(tracks, preferredLanguage = "fin")?.index)
    }

    @Test
    fun initialSubtitle_fallsBackToDefaultTrack() {
        val tracks = listOf(track(1, "fin"), track(2, "eng", isDefault = true))

        assertEquals(2, initialSubtitle(tracks, preferredLanguage = "swe")?.index)
    }

    @Test
    fun initialSubtitle_noPreferenceAndNoDefault_isOff() {
        assertNull(initialSubtitle(listOf(track(1, "eng")), preferredLanguage = null))
    }
}
