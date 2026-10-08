package com.stoganet.tv.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.stoganet.core.AppRoutes
import com.stoganet.tv.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawerScaffoldTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val moviesSelectedDescription =
        context.getString(R.string.nav_item_selected_description, context.getString(R.string.nav_movies))

    @Test
    fun backFromContent_focusesSelectedDrawerItem() = runAndroidComposeUiTest<ComponentActivity> {
        var outerBackCount = 0
        setContent {
            BackHandler { outerBackCount++ }
            DrawerScaffold(currentRoute = AppRoutes.LIBRARY_MOVIES, navigateTo = {}) {
                Box(Modifier.size(100.dp).testTag("content").focusable())
            }
        }
        onNodeWithTag("content").requestFocus()
        waitForIdle()

        activity?.onBackPressedDispatcher?.onBackPressed()
        waitForIdle()

        onNodeWithContentDescription(moviesSelectedDescription).assertIsFocused()
        assertEquals(0, outerBackCount)
    }

    @Test
    fun backFromDrawer_passesBackThrough() = runAndroidComposeUiTest<ComponentActivity> {
        var outerBackCount = 0
        setContent {
            BackHandler { outerBackCount++ }
            DrawerScaffold(currentRoute = AppRoutes.LIBRARY_MOVIES, navigateTo = {}) {
                Box(Modifier.size(100.dp).testTag("content").focusable())
            }
        }
        onNodeWithTag("content").requestFocus()
        waitForIdle()

        activity?.onBackPressedDispatcher?.onBackPressed()
        waitForIdle()
        activity?.onBackPressedDispatcher?.onBackPressed()
        waitForIdle()

        assertEquals(1, outerBackCount)
    }
}
