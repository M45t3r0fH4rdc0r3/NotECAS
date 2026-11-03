package de.mohcomputer.notecas.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import de.mohcomputer.notecas.BuildConfig
import de.mohcomputer.notecas.data.LegacyMeterRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h480dp")
class NotEcasAppInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val electricity = meter(1, "Strom", "kWh")
    private val gas = meter(2, "Gas", "m³")

    @Test fun `analysis exposes meter chooser and can switch meters`() {
        var selectedMeter: Long? = null
        render(
            NotEcasUiState(
                destination = RootDestination.ANALYSIS,
                meters = listOf(electricity, gas),
                selectedMeterId = electricity.id,
            ),
        ) { action -> if (action is UiAction.SelectMeter) selectedMeter = action.id }

        compose.onNodeWithText("Zähler auswählen").assertIsDisplayed()
        compose.onNodeWithText("Zähler auswählen").performClick()
        compose.onNodeWithText("Gas").performClick()
        assertEquals(gas.id, selectedMeter)
    }

    @Test fun `meter editor final controls remain reachable on a small screen`() {
        render(NotEcasUiState(meterEditor = MeterEditor(id = 1, name = "Strom", unit = "kWh")))

        compose.onNode(hasScrollAction()).performTouchInput { repeat(7) { swipeUp() } }
        compose.onNodeWithText("Erweiterte Diagrammeinstellungen").assertIsDisplayed().performClick()
        compose.onNode(hasScrollAction()).performTouchInput { repeat(3) { swipeUp() } }
        compose.onNodeWithText("Beeinflusst nur die Trenddarstellung – niemals Messwerte, Verbrauch oder Kosten.").assertIsDisplayed()
        compose.onNodeWithText("Trendglättung 50").assertIsDisplayed()
    }

    @Test fun `analysis year selector dispatches previous year navigation`() {
        var delta: Int? = null
        render(
            NotEcasUiState(
                destination = RootDestination.ANALYSIS,
                meters = listOf(electricity),
                selectedMeterId = electricity.id,
                analysisYear = 2025,
            ),
        ) { action -> if (action is UiAction.ChangeAnalysisYear) delta = action.delta }

        compose.onNodeWithText("Jahr 2025").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vorheriges Jahr").performClick()
        assertEquals(-1, delta)
    }

    @Test fun `overview renders the colorful dashboard hero`() {
        render(NotEcasUiState(meters = listOf(electricity), readings = mapOf(electricity.id to emptyList())))

        compose.onNodeWithText("Dein Verbrauch. Klar im Blick.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Verbrauchsübersicht").assertIsDisplayed()
    }

    @Test fun `reading editor shows real color choices and dispatches the selected color`() {
        var selectedColor: Int? = null
        render(
            NotEcasUiState(
                readingEditor = ReadingEditor(
                    meterId = electricity.id,
                    date = "2026-11-03",
                    time = "12:00:00",
                ),
            ),
        ) { action -> if (action is UiAction.SetReadingEditor) selectedColor = action.editor.color }

        compose.onNode(hasScrollAction()).performTouchInput { repeat(4) { swipeUp() } }
        compose.onNodeWithContentDescription("Rot auswählen").assertIsDisplayed().performClick()
        assertEquals(1, selectedColor)
    }

    @Test fun `overflow menu opens donation dialog without a duplicate version item`() {
        var openedOverlay: Overlay? = null
        render(NotEcasUiState()) { action -> if (action is UiAction.ShowOverlay) openedOverlay = action.overlay }

        compose.onNodeWithContentDescription("Mehr Optionen").performClick()
        compose.onAllNodesWithText("Version ${BuildConfig.VERSION_NAME}").assertCountEquals(0)
        compose.onNodeWithText("Kleine Spende via PayPal").performClick()
        assertEquals(Overlay.DONATION, openedOverlay)
    }

    @Test fun `info dialog contains complete build and compatibility details`() {
        render(NotEcasUiState(overlay = Overlay.ABOUT))

        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.APPLICATION_ID).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { repeat(3) { swipeUp() } }
        compose.onNodeWithText("Ziel: Android 17 · API 37").assertIsDisplayed()
        compose.onNodeWithText("ECAS-Datenbanken bis Version 17").assertIsDisplayed()
        compose.onNodeWithText("Freie Software unter GNU GPLv3 oder später · ohne Gewährleistung").assertIsDisplayed()
    }

    @Test fun `donation dialog copies address without opening a browser`() {
        var copied = false
        render(NotEcasUiState(overlay = Overlay.DONATION), onCopyDonationAddress = { copied = true })

        compose.onNodeWithText("service@moh-computer.de").assertIsDisplayed()
        compose.onNodeWithText("Adresse kopieren").performClick()
        assertTrue(copied)
        compose.onNodeWithText("PayPal-App nicht installiert").assertIsDisplayed()
    }

    private fun render(
        state: NotEcasUiState,
        onCopyDonationAddress: () -> Unit = {},
        onOpenPayPalApp: () -> Unit = {},
        payPalAppAvailable: Boolean = false,
        onAction: (UiAction) -> Unit = {},
    ) {
        compose.setContent {
            NotEcasApp(
                initialState = state,
                onStateConsumer = {},
                onAction = onAction,
                onImportDatabase = {},
                onExportDatabase = {},
                onImportCsv = {},
                onExportCsv = {},
                torchAvailable = false,
                onTorch = {},
                onCopyDonationAddress = onCopyDonationAddress,
                onOpenPayPalApp = onOpenPayPalApp,
                payPalAppAvailable = payPalAppAvailable,
            )
        }
    }

    private fun meter(id: Long, name: String, unit: String) = LegacyMeterRecord(
        id = id,
        name = name,
        units = unit,
        comment = null,
        vsf = 50,
        tsf = 50,
        cost = 0.3,
        fixedCost = 0.0,
        invert = false,
        vmsetup = null,
        type = 0,
        currency = "€",
        scaling = 1.0,
        physicalUnits = unit,
        bidirectional = false,
        production = false,
    )
}
