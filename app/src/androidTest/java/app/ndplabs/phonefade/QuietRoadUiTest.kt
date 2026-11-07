package app.ndplabs.phonefade

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
        compose.onNodeWithText("Default custom duration (minutes)").performTextReplacement("7")
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
        var selectedMinutes = 0
        val automatic = QuietRoadPreviewStates().values.elementAt(1)
        compose.setContent { FadeTheme { StatusHero(automatic, 0, {}, { selectedMinutes = it }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("The timer continues while locked or screen-off. Returns to monitoring after this time. A qualifying break still resets the main session.").assertExists()
        compose.onNodeWithText("5 minutes").assertExists()
        compose.onNodeWithText("15 minutes").assertExists()
        compose.onNodeWithText("30 minutes").performClick()
        compose.onNodeWithText("1 hour").assertExists()
        compose.onNodeWithText("Use color").performClick()
        compose.runOnIdle { assertEquals(30, selectedMinutes) }
        compose.onNodeWithText("Disable and restore").assertExists()
    }
    @Test fun lockedTemporaryColorShowsElapsedRemainderAndHonestExpiry() {
        val now = mutableStateOf(0L)
        val temporary = QuietRoadPreviewStates().values.elementAt(3).let { state ->
            state.copy(checkpoint = state.checkpoint.copy(session = state.checkpoint.session.copy(
                activity = DeviceActivity.LOCKED, temporary = TemporaryColorState(60_000, activeSince = 0))))
        }
        compose.setContent { FadeTheme { StatusHero(temporary, now.value, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Temporary color remaining, 1 minute").assertExists()
        compose.onNodeWithText("Runs while locked or screen-off. Returns to monitoring after this time. A qualifying break still resets your color allowance.").assertExists()
        compose.runOnIdle { now.value = 30_000 }
        compose.onNodeWithContentDescription("Temporary color remaining, 30 seconds").assertExists()
        compose.runOnIdle { now.value = 60_000 }
        compose.onNodeWithText("Grayscale pending unlock").assertExists()
        compose.onNodeWithText("Use temporary color").assertDoesNotExist()
    }
    @Test fun temporaryColorPickerBlocksInvalidCustomAndAcceptsValidValue() {
        var selectedMinutes = 0
        val automatic = QuietRoadPreviewStates().values.elementAt(1)
        compose.setContent { FadeTheme { StatusHero(automatic, 0, {}, { selectedMinutes = it }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Custom duration (minutes)").performTextReplacement("121")
        compose.onNodeWithText("Use color").assertIsNotEnabled()
        compose.onNodeWithText("Custom duration (minutes)").performTextReplacement("90")
        compose.onNodeWithText("Use color").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(90, selectedMinutes) }
    }
    @Test fun pickerDefaultsToFiveAndReopeningDiscardsCancelledDraft() {
        var selectedMinutes = 0
        val automatic = QuietRoadPreviewStates().values.elementAt(1).let {
            it.copy(savedConfig = it.savedConfig.copy(temporaryMinutes = 17))
        }
        compose.setContent { FadeTheme { StatusHero(automatic, 0, {}, { selectedMinutes = it }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("5 minutes").assertIsSelected()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Custom duration (minutes)").assertTextContains("17")
        compose.onNodeWithText("Custom duration (minutes)").performTextReplacement("42")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, selectedMinutes) }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("5 minutes").assertIsSelected()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Custom duration (minutes)").assertTextContains("17")
    }
    @Test fun pickerRestoresCustomDraftAndSelectionAcrossRecreation() {
        var selectedMinutes = 0
        val restoration = StateRestorationTester(compose)
        val automatic = QuietRoadPreviewStates().values.elementAt(1)
        restoration.setContent { FadeTheme { StatusHero(automatic, 0, {}, { selectedMinutes = it }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Custom duration (minutes)").performTextReplacement("42")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Custom").assertIsSelected()
        compose.onNodeWithText("Custom duration (minutes)").assertTextContains("42")
        compose.runOnIdle { assertEquals(0, selectedMinutes) }
        compose.onNodeWithText("Use color").performClick()
        compose.runOnIdle { assertEquals(42, selectedMinutes) }
    }
    @Test fun pickerClosesWhenAutomaticGrayscaleBecomesIneligible() {
        var requests = 0
        val automatic = QuietRoadPreviewStates().values.elementAt(1)
        val state = mutableStateOf(automatic)
        compose.setContent { FadeTheme { StatusHero(state.value, 0, {}, { requests++ }, {}) } }
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("30 minutes").performClick()
        compose.runOnIdle { state.value = automatic.copy(checkpoint = automatic.checkpoint.copy(
            session = automatic.checkpoint.session.copy(activity = DeviceActivity.LOCKED))) }
        compose.onNodeWithText("Use color").assertDoesNotExist()
        compose.runOnIdle { state.value = automatic }
        compose.onNodeWithText("Use color").assertDoesNotExist()
        compose.onNodeWithText("Use temporary color").performClick()
        compose.onNodeWithText("5 minutes").assertIsSelected()
        compose.runOnIdle { assertEquals(0, requests) }
    }
    @Test fun pickerValidatesWholeMinutesAndBothCustomBounds() {
        var selectedMinutes = 0
        compose.setContent { FadeTheme { TemporaryColorPicker(17, {}, { selectedMinutes = it }) } }
        compose.onNodeWithText("Custom").performClick()
        for (invalid in listOf("", "0", "-1", "121", "1.5", "abc", "999999999999")) {
            compose.onNodeWithText("Custom duration (minutes)").performTextReplacement(invalid)
            compose.onNodeWithText("Use color").assertIsNotEnabled()
        }
        for (minutes in listOf(1, 120)) {
            compose.onNodeWithText("Custom duration (minutes)").performTextReplacement(minutes.toString())
            compose.onNodeWithText("Use color").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(minutes, selectedMinutes) }
        }
    }
    @Test fun pickerPresetsSubmitTheirExplicitDurations() {
        var selectedMinutes = 0
        compose.setContent { FadeTheme { TemporaryColorPicker(17, {}, { selectedMinutes = it }) } }
        for ((label, minutes) in listOf("5 minutes" to 5, "15 minutes" to 15, "30 minutes" to 30, "1 hour" to 60)) {
            compose.onNodeWithText(label).performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Use color").performClick()
            compose.runOnIdle { assertEquals(minutes, selectedMinutes) }
        }
    }
    @Test fun unavailableSettingsPreserveDraftAndDisableSave() {
        compose.setContent { FadeTheme { TimingSettings(MonitoringConfig(), false) {} } }
        compose.onNodeWithText("Edit timing settings").performClick()
        compose.onNodeWithText("Color allowance (minutes)").assertIsNotEnabled()
        compose.onNodeWithText("Save settings").assertIsNotEnabled()
    }
}
