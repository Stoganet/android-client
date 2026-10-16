package com.stoganet.tv

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppTest {

    private val context = ApplicationProvider.getApplicationContext<StoganetApp>()
    private val loginTitle = context.getString(R.string.login_title)
    private val homeNavDescription =
        context.getString(R.string.nav_item_selected_description, context.getString(R.string.nav_home))

    private fun ComposeUiTest.showApp(state: MainActivityUiState) {
        setContent { App(state) }
    }

    @Test
    fun showsNoLogin_whileSignInIsLoading() = runComposeUiTest {
        showApp(MainActivityUiState.Loading)

        onNodeWithText(loginTitle).assertDoesNotExist()
    }

    @Test
    fun showsHome_whenSignedIn() = runComposeUiTest {
        showApp(MainActivityUiState.SignedIn)

        onNodeWithContentDescription(homeNavDescription).assertExists()
        onNodeWithText(loginTitle).assertDoesNotExist()
    }

    @Test
    fun showsLogin_whenSignedOut() = runComposeUiTest {
        showApp(MainActivityUiState.SignedOut)

        onNodeWithText(loginTitle).assertIsDisplayed()
    }
}
