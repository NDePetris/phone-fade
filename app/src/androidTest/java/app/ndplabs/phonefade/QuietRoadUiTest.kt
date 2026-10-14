package app.ndplabs.phonefade

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic presentation only: callbacks never invoke secure settings or monitoring. */
class QuietRoadUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disclosureKeepsDraftAcrossClosingAndRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FadeTheme { TimingSettings(MonitoringConfig(), true) {} } }
        compose.onNodeWithText("Edit timing settings").assert(hasStateDescription("Collapsed")).performClick()
        compose.onNodeWithText("Color allowance (minutes)").performTextReplacement("15")
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Active: 10 minutes").assertExists()
        compose.onNodeWithText("Edit timing settings").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Color allowance (minutes)").assertTextContains("15")
        compose.onNodeWithText("Save settings").assertIsEnabled()
    }
    @Test fun invalidAndUnchangedSaveDisabled() {
        compose.setContent { FadeTheme { TimingSettings(MonitoringConfig(), true) {} } }
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithText("Reset interval (minutes)").performTextReplacement("121")
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithText("Enter a whole number from 1 to 120. Active: 5 minutes").assertExists()
    }
    @Test fun manualHeroPrioritizesRestoreWithoutAutomaticBreakPromise() {
        var restores = 0
        val manual = QuietRoadPreviewStates().values.elementAt(2)
        compose.setContent { FadeTheme { StatusHero(manual, 0, { restores++ }, {}, {}) } }
        compose.onNodeWithContentDescription("Manual grayscale active").assertExists()
        compose.onNodeWithText("Automatic breaks are off. Restore original settings when you are ready.").assertExists()
        compose.onNodeWithText("Use temporary color").assertDoesNotExist()
        compose.onNodeWithText("Restore original settings").performClick()
        compose.runOnIdle { assertEquals(1, restores) }
    }
    @Test fun temporaryHeroHasIndependentStaticCountdownAndNoNewActions() {
        val temporary = QuietRoadPreviewStates().values.elementAt(3)
        compose.setContent { FadeTheme { StatusHero(temporary, 0, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Temporary color remaining, 32 seconds").assertExists()
        compose.onNodeWithText("Use temporary color").assertDoesNotExist()
        compose.onNodeWithText("Disable and restore").assertIsEnabled()
    }
    @Test fun automaticHeroOffersEligibleTemporaryColor() {
        var overrides = 0
        val automatic = QuietRoadPreviewStates().values.elementAt(1)
        compose.setContent { FadeTheme { StatusHero(automatic, 0, {}, { overrides++ }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.runOnIdle { assertEquals(1, overrides) }
        compose.onNodeWithText("Disable and restore").assertExists()
    }
    @Test fun unavailableSettingsPreserveDraftAndDisableSave() {
        compose.setContent { FadeTheme { TimingSettings(MonitoringConfig(), false) {} } }
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Color allowance (minutes)").assertIsNotEnabled()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
    }
}
