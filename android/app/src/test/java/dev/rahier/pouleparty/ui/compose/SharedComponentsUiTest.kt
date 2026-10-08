package dev.rahier.pouleparty.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rahier.pouleparty.ui.common.ActionErrorDialog
import dev.rahier.pouleparty.ui.common.LoadState
import dev.rahier.pouleparty.ui.common.LoadStateScreen
import dev.rahier.pouleparty.ui.components.PrimaryButton
import dev.rahier.pouleparty.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class SharedComponentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a failed load explains why and retries`() {
        var retries = 0
        var backs = 0
        compose.setContent {
            LoadStateScreen(LoadState.Failed(R.string.api_error_network), onRetry = { retries++ }, onBack = { backs++ })
        }
        compose.onNodeWithText("Could not load the game").assertIsDisplayed()
        compose.onNodeWithText("No connection. Check your network and try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun `retry also works from the keyboard`() {
        var retries = 0
        compose.setContent {
            LoadStateScreen(LoadState.Failed(R.string.api_error_unknown), onRetry = { retries++ }, onBack = {})
        }
        compose.onNodeWithText("Try again").requestFocus().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, retries)
    }

    @Test
    fun `an action error offers retry and cancel`() {
        var retried = false
        var dismissed = false
        compose.setContent {
            ActionErrorDialog(
                title = R.string.leave_game_failed_title,
                message = R.string.api_error_chicken_cannot_leave,
                onRetry = { retried = true },
                onDismiss = { dismissed = true },
            )
        }
        compose.onNodeWithText("The chicken cannot leave. Cancel the game instead.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, retried)
        assertEquals(true, dismissed)
    }

    @Test
    fun `a disabled primary button ignores taps`() {
        var taps = 0
        compose.setContent { PrimaryButton(text = "Join", onClick = { taps++ }, enabled = false) }
        compose.onNodeWithText("Join").assertIsNotEnabled()
        compose.onNodeWithText("Join").performClick()
        assertEquals(0, taps)
    }
}
