package de.mohcomputer.notecas.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import de.mohcomputer.notecas.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `launcher renders German navigation and empty state`() {
        compose.onAllNodesWithText("Übersicht")[0].assertIsDisplayed()
        compose.onNodeWithText("Zähler").assertIsDisplayed()
        compose.onNodeWithText("Messwerte").assertIsDisplayed()
        compose.onNodeWithText("Analyse").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Zähler").assertIsDisplayed()
    }
}
