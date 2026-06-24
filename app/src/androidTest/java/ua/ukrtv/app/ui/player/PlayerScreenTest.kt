package ua.ukrtv.app.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerScreen_showsLoadingInitially() {
        composeTestRule.setContent {
            PlayerScreen(
                url = "https://uakino.best/test",
                contentId = "test-1",
                title = "Test Movie",
                onBack = { }
            )
        }

        composeTestRule
            .onNodeWithText("Завантаження...")
            .exists()
    }

    @Test
    fun playerScreen_showsErrorState() {
        composeTestRule.setContent {
            PlayerScreen(
                url = "",
                contentId = "test-error",
                title = "Error Movie",
                onBack = { }
            )
        }

        composeTestRule
            .onNodeWithText("ПОВТОРИТИ")
            .exists()
    }
}
