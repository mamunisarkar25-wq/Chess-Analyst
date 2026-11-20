package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-420dpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches Chess Analyst`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Chess Analyst", context.getString(R.string.app_name))
    }

    @Test
    fun `home screen displays all three primary actions and navigates to PGN, FEN, and ChessCom screens`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_import_pgn_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_import_fen_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chess_com_card").assertIsDisplayed()

        // Navigate to PGN Import screen
        composeTestRule.onNodeWithTag("home_import_pgn_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pgn_open_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pgn_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to FEN Import screen and verify board is shown
        composeTestRule.onNodeWithTag("home_import_fen_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chess_board").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fen_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Chess.com screen and verify public username input (no password)
        composeTestRule.onNodeWithTag("home_chess_com_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chesscom_username_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("Public username only — never asks for a password").assertIsDisplayed()
    }
}
