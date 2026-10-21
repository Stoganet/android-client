package com.stoganet.tv.ui.episode

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.stoganet.tv.R
import com.stoganet.tv.ui.detail.CastMemberUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h300dp")
class EpisodeScreenTest {

    private fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun fakeContent(overview: String = "An episode overview.") = EpisodeUiState.Content(
        playId = "ep1",
        title = "Test Episode",
        runtime = "48m",
        overview = overview,
        cast = persistentListOf(CastMemberUiState("Test Actor", "Actor")),
        isPlayable = true,
        seriesTitle = "Test Show",
        seasonNumber = 1,
        episodeNumber = 3,
        nextEpisodeId = "ep2",
    )

    @Test
    fun longOverview_inShortWindow_focusesPlayButton() = runComposeUiTest {
        setContent {
            EpisodeScreen(
                state = fakeContent(overview = "An episode overview. ".repeat(200)),
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
                onNavigateToEpisode = {},
            )
        }

        onNodeWithContentDescription(str(R.string.detail_play_content_description, "Test Episode")).assertIsFocused()
    }

    @Test
    fun errorState_focusesRetryButton() = runComposeUiTest {
        setContent {
            EpisodeScreen(
                state = EpisodeUiState.Error,
                onIntent = {},
                onNavigateToPlayer = { _, _ -> },
                onNavigateToEpisode = {},
            )
        }

        onNodeWithContentDescription(str(R.string.action_retry)).assertIsFocused()
    }
}
