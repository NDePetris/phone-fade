package app.ndplabs.phonefade

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNodeWithText("Active: 10 minutes").assertDoesNotExist()
        compose.onNodeWithText("Active: 5 minutes").assertDoesNotExist()
        compose.onNodeWithText("Reset interval (minutes)").performTextReplacement("121")
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
        compose.onNodeWithText("Enter a whole number from 1 to 120. Active: 5 minutes").assertExists()
    }
    @Test fun onlyChangedFieldShowsSavedHint() {
        compose.setContent { FadeTheme { TimingSettings(MonitoringConfig(), true) {} } }
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Temporary color duration (minutes)").performTextReplacement("7")
        compose.onNodeWithText("Active: 5 minutes").assertExists()
        compose.onNodeWithText("Active: 10 minutes").assertDoesNotExist()
        compose.onNodeWithText("Unsaved changes").assertExists()
        compose.onNodeWithText("Save settings").assertIsEnabled()
    }
    @Test fun enlargedFloatingLabelsDoNotOverlapSavedHint() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FadeTheme {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        TimingSettings(MonitoringConfig(), true) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Color allowance (minutes)").performTextReplacement("15")
        compose.waitForIdle()
        val hint = compose.onNodeWithText("Active: 10 minutes", useUnmergedTree = true).fetchSemanticsNode()
        val nextLabel = compose.onNodeWithText("Reset interval (minutes)", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Enlarged label must clear the preceding saved hint",
            hint.positionInRoot.y + hint.size.height <= nextLabel.positionInRoot.y)
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
