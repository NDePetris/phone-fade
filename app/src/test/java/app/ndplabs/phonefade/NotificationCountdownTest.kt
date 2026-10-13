package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class NotificationCountdownTest {
    private val baseline = ColorSettings("0", "12")
    private fun color(config: MonitoringConfig = MonitoringConfig(2, 5)) = MonitoringState(
        MonitoringCheckpoint(config, SessionState(enabled = true, hasSession = true,
            activity = DeviceActivity.UNLOCKED, activeSince = 1000, observedAt = 1000)),
        ControllerStatus(permissionGranted = true, current = baseline, readable = true), running = true, ready = true)
    private fun temporary() = color().let { it.copy(checkpoint = it.checkpoint.copy(
        session = it.checkpoint.session.copy(consumedMs = 120_000, exhausted = true, activeSince = null,
            temporary = TemporaryColorState(120_000, 20_000, 1000)), expectedBaseline = baseline)) }
    private fun observe(state: MonitoringState, activity: DeviceActivity, now: Long) = state.copy(checkpoint = state.checkpoint.copy(
        session = SessionEngine.reduce(state.checkpoint.session, SessionEvent.Observe(activity), now, state.checkpoint.config).state))

    @Test fun minuteLabelsKeepSingularAndPluralUntilFinalMinute() {
        val state = color()
        assertEquals("Color: 2 minutes remaining", NotificationCountdown.presentation(state, 1000).text)
        assertEquals("Color: 2 minutes remaining", NotificationCountdown.presentation(state, 60_999).text)
        assertNull(NotificationCountdown.presentation(state, 60_999).deadlineElapsed)
        assertEquals("1 minute remaining", CountdownFormatter.remaining(60_000, true))
        val lastMinute = NotificationCountdown.presentation(state, 61_000)
        assertEquals("Color: Less than a minute remaining", lastMinute.text)
        assertEquals(121_000L, lastMinute.deadlineElapsed)
    }
    @Test fun nativeCountdownUsesTheSameMonotonicDeadlineAsTheEngine() {
        val state = color()
        for (now in listOf(61_000L, 70_000L, 120_999L)) {
            assertEquals(state.checkpoint.session.deadline(state.checkpoint.config),
                NotificationCountdown.presentation(state, now).deadlineElapsed)
        }
        assertNull(NotificationCountdown.presentation(state, 121_000).deadlineElapsed)
        assertEquals("Applying grayscale…", NotificationCountdown.presentation(state, 121_001).text)
    }
    @Test fun sameDeadlineDoesNotRequirePerSecondNotificationRebuilds() {
        val state = color()
        val first = NotificationCountdown.presentation(state, 61_000)
        for (now in 61_001L..120_999L step 1000) assertEquals(first, NotificationCountdown.presentation(state, now))
    }
    @Test fun temporaryMinuteCopyUsesTheFullLabelAndRoundedPlural() {
        val state = temporary()
        assertEquals("Temporary color: 2 minutes remaining", NotificationCountdown.presentation(state, 1000).text)
        assertEquals("Temporary color: 2 minutes remaining", NotificationCountdown.presentation(state, 40_999).text)
        val finalMinute = NotificationCountdown.presentation(state, 41_000)
        for (now in 41_001L..100_999L step 1000) assertEquals(finalMinute, NotificationCountdown.presentation(state, now))
    }
    @Test fun pausedMinuteCopyUsesSingularAtExactlyOneMinute() {
        for (state in listOf(color(), temporary())) {
            val label = if (state.checkpoint.session.temporary != null) "Temporary color" else "Color"
            val deadline = state.checkpoint.session.deadline(state.checkpoint.config)!!
            val paused = observe(state, DeviceActivity.OFF, deadline - 60_000)
            assertEquals(NotificationPresentation("$label: 1 minute remaining · Paused"),
                NotificationCountdown.presentation(paused, deadline + 100_000))
            val earlier = observe(state, DeviceActivity.OFF, deadline - 60_001)
            assertEquals(NotificationPresentation("$label: 2 minutes remaining · Paused"),
                NotificationCountdown.presentation(earlier, deadline + 100_000))
        }
    }
    @Test fun pausedPositiveSubsecondTimeNeverShowsZero() {
        for (state in listOf(color(), temporary())) {
            val label = if (state.checkpoint.session.temporary != null) "Temporary color" else "Color"
            val deadline = state.checkpoint.session.deadline(state.checkpoint.config)!!
            val paused = observe(state, DeviceActivity.OFF, deadline - 1)
            assertEquals(NotificationPresentation("$label: 0:01 remaining · Paused"),
                NotificationCountdown.presentation(paused, deadline + 100_000))
        }
    }
    @Test fun offAndLockFreezeSecondsAndUnlockMovesOnlyTheDisplayDeadline() {
        val active = color()
        val off = observe(active, DeviceActivity.OFF, 80_000)
        val paused = NotificationCountdown.presentation(off, 90_000)
        assertNull(paused.deadlineElapsed); assertEquals("Color: 0:41 remaining · Paused", paused.text)
        assertEquals(paused, NotificationCountdown.presentation(off, 200_000))
        val locked = observe(off, DeviceActivity.LOCKED, 100_000)
        assertEquals("Color: 0:41 remaining · Paused", NotificationCountdown.presentation(locked, 110_000).text)
        assertNull(NotificationCountdown.presentation(locked, 110_000).deadlineElapsed)
        val unlocked = observe(locked, DeviceActivity.UNLOCKED, 110_000)
        assertEquals(151_000L, NotificationCountdown.presentation(unlocked, 110_000).deadlineElapsed)
    }
    @Test fun temporaryColorUsesItsCapturedDurationAndPreservesOrdinaryExhaustion() {
        val state = temporary()
        assertEquals("Temporary color: Less than a minute remaining", NotificationCountdown.presentation(state, 41_000).text)
        assertEquals(101_000L, NotificationCountdown.presentation(state, 41_000).deadlineElapsed)
        val changed = state.copy(checkpoint = state.checkpoint.copy(config = MonitoringConfig(120, 5, 120)))
        assertEquals(NotificationCountdown.presentation(state, 41_000), NotificationCountdown.presentation(changed, 41_000))
        assertTrue(changed.checkpoint.session.exhausted)
        val off = observe(state, DeviceActivity.OFF, 60_000)
        assertEquals("Temporary color: 0:41 remaining · Paused", NotificationCountdown.presentation(off, 90_000).text)
        val unlocked = observe(off, DeviceActivity.UNLOCKED, 90_000)
        assertEquals(131_000L, NotificationCountdown.presentation(unlocked, 90_000).deadlineElapsed)
    }
    @Test fun pendingAttentionStartupAndStoppedStatesHaveNoNativeCountdown() {
        val state = color()
        val candidates = listOf(state.copy(running = false), state.copy(starting = true), state.copy(ready = false),
            state.copy(checkpoint = state.checkpoint.copy(attention = "Interrupted")),
            state.copy(checkpoint = state.checkpoint.copy(pending = DisplayEffect.ENABLE)),
            state.copy(checkpoint = state.checkpoint.copy(session = state.checkpoint.session.copy(enabled = false))))
        for (candidate in candidates) assertNull(NotificationCountdown.presentation(candidate, 80_000).deadlineElapsed)
        assertEquals("Monitoring interrupted — enable to resume", NotificationCountdown.presentation(state.copy(running = false), 80_000).text)
    }
    @Test fun interruptedHistoryCanResumeOnlyFromReliableNewAnchor() {
        val state = color().let { it.copy(checkpoint = it.checkpoint.copy(interrupted = true,
            session = it.checkpoint.session.copy(consumedMs = 80_000, activeSince = null))) }
        assertNull(NotificationCountdown.presentation(state, 900_000).deadlineElapsed)
        val recovered = state.copy(checkpoint = state.checkpoint.copy(session = SessionEngine.reduce(
            state.checkpoint.session, SessionEvent.Recover(DeviceActivity.UNLOCKED), 900_000, state.checkpoint.config).state))
        assertEquals(940_000L, NotificationCountdown.presentation(recovered, 900_000).deadlineElapsed)
        assertEquals("Color: Less than a minute remaining", NotificationCountdown.presentation(recovered, 900_000).text)
    }
    @Test fun mismatchedBaselineMissingPermissionAndUnreadableSettingsSuppressTimer() {
        val state = temporary()
        for (display in listOf(state.display.copy(permissionGranted = false), state.display.copy(readable = false),
            state.display.copy(current = ColorSettings("0", "11")),
            state.display.copy(original = baseline, current = ColorSettings("1", "0")))) {
            assertNull(NotificationCountdown.presentation(state.copy(display = display), 80_000).deadlineElapsed)
        }
    }
    @Test fun pausedExpiredOverrideAndOwnedGrayscaleHaveNoRunningTimer() {
        val expired = observe(temporary(), DeviceActivity.OFF, 101_000)
        assertNull(NotificationCountdown.presentation(expired, 120_000).deadlineElapsed)
        assertTrue(NotificationCountdown.presentation(expired, 120_000).text.contains("grayscale pending unlock"))
        val grayscale = color().let { it.copy(checkpoint = it.checkpoint.copy(session = it.checkpoint.session.copy(exhausted = true)),
            display = it.display.copy(original = baseline, current = ColorSettings("1", "0"))) }
        assertEquals(NotificationPresentation("Grayscale active"), NotificationCountdown.presentation(grayscale, 80_000))
    }
    @Test fun epochProjectionRoundUpDoesNotAlterLogicalDeadlineAndTracksWallClockChanges() {
        val plan = NotificationPresentation("Color: Less than a minute remaining", 100_000)
        val wall = 1_700_000_000_000L
        assertEquals(wall + 50_999L, plan.whenMillis(50_000, wall))
        assertEquals(wall + 50_999L, plan.whenMillis(60_000, wall + 10_000))
        assertEquals(wall + 3_650_999L, plan.whenMillis(60_000, wall + 3_610_000))
        assertEquals(100_000L, plan.deadlineElapsed)
        assertNull(NotificationPresentation("Paused").whenMillis(50_000, wall))
    }
    @Test fun thresholdSchedulingAddsOneBoundaryWithoutPolling() {
        assertEquals(60_000L, NotificationCountdown.nextWakeDelay(180_000, 0))
        assertEquals(30_001L, NotificationCountdown.nextWakeDelay(90_001, 0))
        assertEquals(1L, NotificationCountdown.nextWakeDelay(60_001, 0))
        assertEquals(60_000L, NotificationCountdown.nextWakeDelay(60_000, 0))
        assertEquals(59_999L, NotificationCountdown.nextWakeDelay(59_999, 0))
        assertEquals(1000L, NotificationCountdown.nextWakeDelay(1000, 0))
        assertEquals(0L, NotificationCountdown.nextWakeDelay(1000, 2000))
    }
    @Test fun disablingAndQualifyingResetReplaceOverrideCountdown() {
        val state = temporary()
        val disabled = state.copy(running = false, checkpoint = state.checkpoint.copy(session = SessionState()))
        assertEquals(NotificationPresentation("Monitoring disabled"), NotificationCountdown.presentation(disabled, 80_000))
        val off = observe(state, DeviceActivity.OFF, 60_000)
        val reset = observe(off, DeviceActivity.LOCKED, 360_000)
        assertNull(reset.checkpoint.session.temporary)
        assertNull(NotificationCountdown.presentation(reset, 360_000).deadlineElapsed)
        assertEquals("Color: 2 minutes remaining · Paused", NotificationCountdown.presentation(reset, 360_000).text)
    }
    @Test fun delayedPublicationNeverStartsAnAlreadyExpiredChronometer() {
        val plan = NotificationCountdown.presentation(color(), 120_999)
        assertNotNull(plan.deadlineElapsed)
        assertNull(plan.whenMillis(121_000, 1_700_000_000_000))
        assertNull(plan.whenMillis(121_999, 1_700_000_000_999))
        assertEquals("Checking monitoring — open Offramp Fade", plan.textAt(121_000))
        assertEquals(plan.text, plan.textAt(120_999))
        assertEquals("Paused", NotificationPresentation("Paused").textAt(900_000))
    }
}
