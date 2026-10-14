package app.ndplabs.phonefade

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class QuietRoadPresentationTest {
    @Test fun spokenDurationPreservesUnitsAndPluralization() {
        assertEquals("1 second", QuietRoadPresentation.spokenCountdown("0:01"))
        assertEquals("32 seconds", QuietRoadPresentation.spokenCountdown("0:32"))
        assertEquals("1 minute", QuietRoadPresentation.spokenCountdown("1 minute"))
        assertEquals("7 minutes", QuietRoadPresentation.spokenCountdown("7 minutes"))
    }
    private val display = ControllerStatus(permissionGranted = true, readable = true, current = ColorSettings("0", "12"))
    private val session = SessionState(enabled = true, hasSession = true, consumedMs = 180_000, activity = DeviceActivity.UNLOCKED)
    private val active = MonitoringState(MonitoringCheckpoint(session = session), display, running = true, ready = true)
    private val gray = display.copy(current = ColorSettings("1", "0"), original = display.current)

    @Test fun ordinaryCountdownAndSecondaryDisable() {
        val hero = QuietRoadPresentation.hero(active, 0)
        assertEquals("Monitoring active", hero.status)
        assertEquals("Color remaining", hero.title)
        assertEquals("7 minutes", hero.countdown)
        assertNull(hero.primary); assertEquals(HeroAction.DISABLE, hero.secondary)
        assertTrue(hero.guidance.contains("only while unlocked"))
    }
    @Test fun manualGuidanceNeverPromisesAutomaticBreak() {
        val hero = QuietRoadPresentation.hero(MonitoringState(display = gray, ready = true), 0)
        assertEquals("Manual grayscale active", hero.title)
        assertEquals("Automatic breaks are off. Restore original settings when you are ready.", hero.guidance)
        assertEquals(HeroAction.RESTORE, hero.primary); assertEquals(HeroAction.START, hero.secondary)
    }
    @Test fun automaticEligibilityRemainsExistingPredicate() {
        val automatic = active.copy(display = gray, checkpoint = active.checkpoint.copy(session = session.copy(exhausted = true)))
        assertEquals(HeroAction.TEMPORARY, QuietRoadPresentation.hero(automatic, 0).primary)
        for (activity in listOf(DeviceActivity.OFF, DeviceActivity.LOCKED)) {
            val paused = automatic.copy(checkpoint = automatic.checkpoint.copy(session = automatic.checkpoint.session.copy(activity = activity)))
            assertNull(QuietRoadPresentation.hero(paused, 0).primary)
            assertFalse(MonitoringPresentation.canUseTemporaryColor(paused))
            assertEquals("Grayscale active", QuietRoadPresentation.hero(paused, 0).title)
        }
        assertNull(QuietRoadPresentation.hero(automatic.copy(display = gray.copy(permissionGranted = false)), 0).primary)
    }
    @Test fun overrideUsesItsCapturedDurationAndHasNoExtensionAction() {
        val temporary = active.copy(checkpoint = active.checkpoint.copy(config = MonitoringConfig(10, 2, 120),
            session = session.copy(exhausted = true, temporary = TemporaryColorState(300_000, consumedMs = 268_000))))
        val hero = QuietRoadPresentation.hero(temporary, 0)
        assertEquals("Temporary color remaining", hero.title); assertEquals("0:32", hero.countdown)
        assertEquals("Counts only while unlocked. This doesn't reset your color allowance.", hero.guidance)
        assertNull(hero.primary)
        assertEquals(HeroAction.DISABLE, hero.secondary)
    }
    @Test fun pauseAndUnlockStayQuietWithStaticCountdown() {
        for ((activity, label) in listOf(DeviceActivity.OFF to "Usage paused", DeviceActivity.LOCKED to "Waiting for unlock")) {
            val state = active.copy(checkpoint = active.checkpoint.copy(session = session.copy(activity = activity)))
            assertEquals(label, QuietRoadPresentation.hero(state, 0).status)
            assertEquals("7 minutes", QuietRoadPresentation.hero(state, 900_000).countdown)
            assertNull(QuietRoadPresentation.hero(state, 0).history)
        }
    }
    @Test fun historicalInterruptionIsSeparateAndFlagUnchanged() {
        val state = active.copy(checkpoint = active.checkpoint.copy(interrupted = true))
        val hero = QuietRoadPresentation.hero(state, 0)
        assertEquals("7 minutes", hero.countdown)
        assertEquals("Some screen time may not have been counted.", hero.history)
        assertTrue(state.checkpoint.interrupted)
        val interrupted = QuietRoadPresentation.hero(state.copy(running = false), 0)
        assertEquals(HeroAction.RESUME, interrupted.primary); assertEquals(HeroAction.DISABLE, interrupted.secondary)
    }
    @Test fun attentionAndPendingTakePrecedenceWithoutDuplicateRecovery() {
        val attention = active.copy(checkpoint = active.checkpoint.copy(attention = "storage failed", interrupted = true))
        val hero = QuietRoadPresentation.hero(attention, 0)
        assertEquals("Review display settings", hero.title); assertNull(hero.countdown)
        assertEquals(HeroAction.DISABLE, hero.primary); assertNull(hero.secondary)
        val pending = active.copy(checkpoint = active.checkpoint.copy(pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.TEMPORARY_COLOR))
        assertEquals("Restoring original settings…", QuietRoadPresentation.hero(pending, 0).title)
        assertNull(QuietRoadPresentation.hero(pending, 0).countdown)
    }
    @Test fun exhaustedUnverifiedStateNeverShowsZeroOrClaimsGrayscale() {
        val exhausted = active.copy(checkpoint = active.checkpoint.copy(session = session.copy(exhausted = true)))
        assertEquals("Applying grayscale…", QuietRoadPresentation.hero(exhausted, 0).title)
        assertNull(QuietRoadPresentation.hero(exhausted, 0).countdown)
    }
    @Test fun draftsAndSaveBoundsDoNotAffectOperationalValues() {
        val saved = MonitoringConfig()
        assertFalse(TimingDraft("10", "5", "5").canSave(saved, true))
        assertFalse(TimingDraft("010", "5", "5").changed(saved))
        for (invalid in listOf("", "0", "121", "1.5", "bad")) {
            assertFalse(TimingDraft(invalid, "5", "5").canSave(saved, true))
        }
        assertTrue(TimingDraft("1", "120", "2").canSave(saved, true))
        assertFalse(TimingDraft("1", "120", "2").canSave(saved, false))
        assertEquals("7 minutes", QuietRoadPresentation.hero(active, 0).countdown)
    }
    @Test fun approvedDisabledHeadlineKeepsFactualStateAndAction() {
        val hero = QuietRoadPresentation.hero(MonitoringState(display = display, ready = true), 0)
        assertEquals("A quieter route awaits", hero.title)
        assertEquals("Monitoring disabled", hero.status)
        assertEquals(HeroAction.START, hero.primary)
        assertEquals("Enable monitoring to start your color allowance. Counts only while unlocked.", hero.guidance)
    }
    @Test fun automaticGuidanceUsesConfiguredDurationAndGrammarWithoutChangingRecovery() {
        for (minutes in listOf(1, 5, 120)) {
            val state = active.copy(display = gray, checkpoint = active.checkpoint.copy(
                config = MonitoringConfig(resetMinutes = minutes), session = session.copy(exhausted = true)))
            assertEquals("Turn your screen off for $minutes uninterrupted ${if (minutes == 1) "minute" else "minutes"} to earn a reset. Color returns when you wake it.",
                QuietRoadPresentation.hero(state, 0).guidance)
            val failed = state.copy(checkpoint = state.checkpoint.copy(attention = "Restore failed"))
            assertFalse(QuietRoadPresentation.hero(failed, 0).guidance.contains("Color returns"))
            assertEquals(HeroAction.DISABLE, QuietRoadPresentation.hero(failed, 0).primary)
        }
    }
    @Test fun savedHintsAppearOnlyForChangedFieldsAndKeepInvalidErrors() {
        assertNull(QuietRoadPresentation.fieldSupportingText("5", 5))
        assertNull(QuietRoadPresentation.fieldSupportingText("005", 5))
        assertEquals("Active: 1 minute", QuietRoadPresentation.fieldSupportingText("2", 1))
        assertEquals("Active: 5 minutes", QuietRoadPresentation.fieldSupportingText("15", 5))
        for (value in listOf("", "0", "121", "bad")) {
            assertEquals("Enter a whole number from 1 to 120. Active: 5 minutes", QuietRoadPresentation.fieldSupportingText(value, 5))
        }
    }
    @Test fun feedbackIsConsumedOnceAcrossCollectorsAndFailuresAreSilent() = runBlocking {
        val queue = CommandFeedbackQueue()
        queue.offer(CommandFeedback.SAVED)
        assertEquals(CommandFeedback.SAVED, queue.events.first())
        queue.offer(CommandFeedback.FAILED)
        assertNull(withTimeoutOrNull(30) { queue.events.first() })
        queue.offer(CommandFeedback.RESTORED)
        assertEquals(CommandFeedback.RESTORED, queue.events.first())
        assertNull(withTimeoutOrNull(30) { queue.events.first() })
        queue.offer(CommandFeedback.NOTHING_TO_RESTORE)
        assertEquals("No saved settings to restore.", queue.events.first().message)
    }
}
