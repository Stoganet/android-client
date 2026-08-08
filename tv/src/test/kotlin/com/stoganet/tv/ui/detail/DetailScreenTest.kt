package com.stoganet.tv.ui.detail

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.stoganet.core.api.model.MediaState
import com.stoganet.core.api.model.MediaType
import com.stoganet.tv.R
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailScreenTest {

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun fakeMovieContent(isPlayable: Boolean = true) = DetailUiState.Content(
        title = "Test Movie",
        year = 1999,
        mediaType = MediaType.MOVIE,
        backdropUrl = null,
        overview = "A computer hacker learns the truth.",
        genres = persistentListOf("Action", "Sci-Fi"),
        runtime = "2h 16m",
        cast = persistentListOf(CastMemberUiState("Test Actor", "Actor")),
        seasons = persistentListOf(),
        resume = null,
        streamUrl = if (isPlayable) "https://api.stoganet.com/stream/abc" else null,
        isPlayable = isPlayable,
        mediaState = MediaState.PLAYABLE,
    )

    private fun fakeTvContent() = DetailUiState.Content(
        title = "Test Show",
        year = 2008,
        mediaType = MediaType.TV,
        backdropUrl = null,
        overview = "A chemistry teacher turns to crime.",
        genres = persistentListOf("Drama", "Crime"),
        runtime = "",
        cast = persistentListOf(CastMemberUiState("Test Actor Three", "Actor")),
        seasons = persistentListOf(
            SeasonUiState(1, "Season 1", 13, null, null),
            SeasonUiState(2, "Season 2", 13, null, null),
            SeasonUiState(3, "Season 3", 10, null, null),
        ),
        resume = null,
        streamUrl = null,
        isPlayable = false,
        mediaState = MediaState.PLAYABLE,
    )

    @Test
    fun loadingState_showsProgressIndicator() = runComposeUiTest {
        setContent { DetailScreen(state = DetailUiState.Loading, onIntent = {}, onNavigateToPlayer = { _, _ -> }) }

        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertIsDisplayed()
    }

    @Test
    fun errorState_showsRetryButton() = runComposeUiTest {
        setContent { DetailScreen(state = DetailUiState.Error, onIntent = {}, onNavigateToPlayer = { _, _ -> }) }

        onNodeWithContentDescription(str(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun retryButton_triggersRetryIntent() = runComposeUiTest {
        var intentFired = false
        setContent {
            DetailScreen(
                state = DetailUiState.Error,
                onIntent = { if (it == DetailIntent.Retry) intentFired = true },
                onNavigateToPlayer = { _, _ -> },
            )
        }

        onNodeWithContentDescription(str(R.string.action_retry)).requestFocus()
        onNodeWithContentDescription(str(R.string.action_retry)).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(intentFired)
    }

    @Test
    fun contentState_movie_showsTitle() = runComposeUiTest {
        setContent { DetailScreen(state = fakeMovieContent(), onIntent = {}, onNavigateToPlayer = { _, _ -> }) }

        onNodeWithText("Test Movie").assertIsDisplayed()
    }

    @Test
    fun contentState_movie_showsPlayButton() = runComposeUiTest {
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = true),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
            )
        }

        onNodeWithContentDescription(str(R.string.detail_play_content_description, "Test Movie")).assertIsDisplayed()
    }

    @Test
    fun contentState_movie_playButton_invokesCallback() = runComposeUiTest {
        var played = false
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = true),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> played = true },
            )
        }

        val playDesc = str(R.string.detail_play_content_description, "Test Movie")
        onNodeWithContentDescription(playDesc).requestFocus()
        onNodeWithContentDescription(playDesc).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(played)
    }

    @Test
    fun contentState_tv_showsSeasonChips() = runComposeUiTest {
        setContent { DetailScreen(state = fakeTvContent(), onIntent = {}, onNavigateToPlayer = { _, _ -> }) }

        onNodeWithContentDescription(str(R.string.detail_season_chip_content_description, 1)).assertIsDisplayed()
        onNodeWithContentDescription(str(R.string.detail_season_chip_content_description, 2)).assertIsDisplayed()
    }

    @Test
    fun contentState_showsCastMemberName() = runComposeUiTest {
        setContent { DetailScreen(state = fakeMovieContent(), onIntent = {}, onNavigateToPlayer = { _, _ -> }) }

        onNodeWithText("Test Actor").assertIsDisplayed()
    }

    @Test
    fun contentState_movie_notPlayable_showsNotAvailableButton() = runComposeUiTest {
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = false),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
            )
        }

        onNodeWithContentDescription(
            str(R.string.detail_not_available_content_description, "Test Movie"),
        ).assertIsDisplayed()
    }

    @Test
    fun contentState_movie_playButton_passesStreamUrlToCallback() = runComposeUiTest {
        var receivedUrl: String? = null
        val expectedUrl = "https://api.stoganet.com/stream/abc"
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = true).copy(streamUrl = expectedUrl),
                onIntent = {},
                onNavigateToPlayer = { url, _ -> receivedUrl = url },
            )
        }

        val playDesc = str(R.string.detail_play_content_description, "Test Movie")
        onNodeWithContentDescription(playDesc).requestFocus()
        onNodeWithContentDescription(playDesc).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(expectedUrl, receivedUrl)
    }

    @Test
    fun contentState_movie_requestable_showsRequestButton() = runComposeUiTest {
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = false).copy(mediaState = MediaState.REQUESTABLE),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
            )
        }

        onNodeWithContentDescription(
            str(R.string.detail_request_content_description, "Test Movie"),
        ).assertIsDisplayed()
    }

    @Test
    fun contentState_movie_requestButton_invokesRequestMovieIntent() = runComposeUiTest {
        var intentFired = false
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = false).copy(mediaState = MediaState.REQUESTABLE),
                onIntent = { if (it == DetailIntent.RequestMovie) intentFired = true },
                onNavigateToPlayer = { _, _ -> },
            )
        }

        val requestDesc = str(R.string.detail_request_content_description, "Test Movie")
        onNodeWithContentDescription(requestDesc).requestFocus()
        onNodeWithContentDescription(requestDesc).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(intentFired)
    }

    @Test
    fun contentState_movie_downloading_showsDownloadingButton() = runComposeUiTest {
        setContent {
            DetailScreen(
                state = fakeMovieContent(isPlayable = false).copy(mediaState = MediaState.DOWNLOADING),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
            )
        }

        onNodeWithContentDescription(
            str(R.string.detail_downloading_content_description, "Test Movie"),
        ).assertIsDisplayed()
    }
}
