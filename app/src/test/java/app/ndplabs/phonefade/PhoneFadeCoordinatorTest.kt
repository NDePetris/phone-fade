package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class PhoneFadeCoordinatorTest {
    private var now = 0L
    private var saved = MonitoringCheckpoint(bootCount = 1, config = MonitoringConfig(1, 1))
    private var diskFailed = false
    private var original: ColorSettings? = null
    private var permission = true
    private var writeFailed = false
    private var current = ColorSettings("0", "12")
    private var writes = 0
    private val controller = GrayscaleController(object : DisplaySettings {
        override fun hasWritePermission() = permission
        override fun read(key: String) = if (key == GrayscaleController.ENABLED) current.enabled else current.mode
        override fun write(key: String, value: String?): Boolean {
            writes++
            if (writeFailed) return false
            current = if (key == GrayscaleController.ENABLED) current.copy(enabled = value) else current.copy(mode = value)
            return true
        }
    }, object : RecoveryStore {
        override fun load() = original
        override fun save(original: ColorSettings) { this@PhoneFadeCoordinatorTest.original = original }
        override fun clear() { original = null }
    })
    private fun coordinator() = PhoneFadeCoordinator(controller, object : MonitoringStore {
        override fun load() = saved
        override fun save(checkpoint: MonitoringCheckpoint) { check(!diskFailed); saved = checkpoint }
    }) { now }.also { it.initialize(1) }
    private fun expire(coordinator: PhoneFadeCoordinator) {
        now += 60_000
        coordinator.event(SessionEvent.Deadline(coordinator.state.value.checkpoint.session.generation))
    }

    @Test fun activationThenShortLockDoesNotWriteAgain() {
        val c = coordinator(); assertTrue(c.start(DeviceActivity.UNLOCKED, true)); expire(c)
        assertTrue(c.state.value.display.ownsGrayscale); val count = writes
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); assertEquals(count, writes)
    }
    @Test fun longBreakRestoresExactly() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); assertEquals(ColorSettings("0", "12"), current); assertNull(original)
        assertFalse(c.state.value.checkpoint.session.exhausted)
    }
    @Test fun pixelLongBreakAndUnlockWithLaggingKeyguardRestoresAndUnpauses() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, DeviceActivity.OFF))
        now += 90_001
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_ON, DeviceActivity.LOCKED))
        assertFalse(saved.session.resetEarned); assertNull(saved.session.breakSince)
        assertNull(saved.session.deadline(saved.config))
        assertFalse(c.state.value.display.ownsGrayscale)
        assertEquals(ColorSettings("0", "12"), current); assertNull(original)
        assertEquals("Color: 1 minute remaining · waiting for unlock", MonitoringNotification.text(c.state.value, now))
        now += 120_000
        assertEquals(60_000L, saved.session.remaining(saved.config, now))
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.LOCKED))
        assertEquals(DeviceActivity.UNLOCKED, saved.session.activity)
        assertFalse(saved.session.resetEarned); assertFalse(saved.session.exhausted)
        assertEquals(60_000L, saved.session.remaining(saved.config, now))
        assertEquals(now, saved.session.activeSince)
        assertEquals(ColorSettings("0", "12"), current); assertNull(original)
        assertNull(saved.pending); assertNull(saved.attention)
        assertEquals("Color: 1 minute remaining", MonitoringNotification.text(c.state.value, now))
        val count = writes
        now += 100
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.LOCKED))
        assertEquals(count, writes); assertEquals(100L, saved.session.consumedMs)
    }
    @Test fun shortBreakUnlockWithLaggingKeyguardKeepsOwnershipAndUnpauses() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        val count = writes
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, DeviceActivity.OFF)); now += 1000
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_ON, DeviceActivity.LOCKED))
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.LOCKED))
        assertEquals(DeviceActivity.UNLOCKED, saved.session.activity)
        assertFalse(saved.session.resetEarned); assertTrue(saved.session.exhausted)
        assertEquals(count, writes); assertEquals(ColorSettings("0", "12"), original)
        assertEquals("Grayscale active", MonitoringNotification.text(c.state.value, now))
    }
    @Test fun delayedUserPresentWhileOffPreservesBreakUntilRealUnlock() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, DeviceActivity.OFF))
        val breakSince = saved.session.breakSince
        now += 90_001
        val count = writes
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.OFF))
        assertEquals(DeviceActivity.OFF, saved.session.activity)
        assertEquals(breakSince, saved.session.breakSince); assertEquals(count, writes)
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_ON, DeviceActivity.LOCKED))
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.LOCKED))
        assertEquals(ColorSettings("0", "12"), current); assertNull(original)
    }
    @Test fun qualifyingWakeWithoutKeyguardRestoresAndStartsFullAllowance() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, DeviceActivity.OFF)); now += 60_000
        c.event(ScreenEventMapper.event(ScreenSignal.SCREEN_ON, DeviceActivity.UNLOCKED))
        assertNull(original); assertEquals(ColorSettings("0", "12"), current)
        assertEquals(now, saved.session.activeSince); assertEquals(60_000L, saved.session.remaining(saved.config, now))
        val count = writes; now += 500
        c.event(ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.UNLOCKED))
        assertEquals(count, writes); assertEquals(500L, saved.session.consumedMs)
    }
    @Test fun unlockedReconciliationResumesFreshAllowanceWithoutUserPresent() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); now += 90_000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertEquals(0L, saved.session.consumedMs); assertEquals(now, saved.session.activeSince)
        assertEquals(60_000L, saved.session.remaining(saved.config, now)); assertNull(saved.attention)
    }
    @Test fun shortWakeRetainsPartialUsageThroughLongKeyguardWait() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 20_000
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 20_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); now += 120_000
        assertEquals(40_000L, saved.session.remaining(saved.config, now))
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertEquals(20_000L, saved.session.consumedMs); assertEquals(0, writes)
    }
    @Test fun repeatedShortLockScreenWakesPreserveGrayscaleAndUsage() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); val count = writes
        repeat(5) {
            c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 40_000
            c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); now += 90_000
            assertTrue(saved.session.exhausted); assertEquals(60_000L, saved.session.consumedMs)
            assertNull(saved.session.breakSince); assertNull(saved.session.activeSince)
            assertEquals("Paused — Grayscale active", MonitoringNotification.text(c.state.value, now))
        }
        assertEquals(count, writes); assertEquals(ColorSettings("0", "12"), original)
    }
    @Test fun duplicateWakeAndDelayedEventsDoNotRepeatResetOrChargeLockedTime() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); val wake = now; val count = writes
        now += 30_000; c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertEquals(0L, saved.session.consumedMs); assertEquals(count, writes)
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); now += 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED), wake)
        c.event(SessionEvent.Recover(DeviceActivity.OFF), wake)
        assertEquals(1000L, saved.session.consumedMs); assertEquals(DeviceActivity.UNLOCKED, saved.session.activity)
        assertEquals(count, writes)
    }
    @Test fun lockedWakeRestoreWriteFailureRetainsSnapshotAndPreviousSession() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000; writeFailed = true
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertEquals(ColorSettings("0", "12"), original); assertTrue(saved.session.exhausted)
        assertEquals(60_000L, saved.session.consumedMs); assertFalse(saved.session.resetEarned)
        assertNull(saved.session.activeSince); assertEquals(DisplayEffect.RESTORE, saved.pending)
        assertNotNull(saved.attention)
        assertEquals("Attention required — open Offramp Fade", MonitoringNotification.text(c.state.value, now))
        val count = writes; now += 90_000; c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertEquals(count, writes); writeFailed = false; c.disable(); assertNull(original)
    }
    @Test fun lockedWakeRestoreProtectsExternalConflict() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000
        current = ColorSettings("1", "11"); val count = writes
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertEquals(count, writes); assertEquals(ColorSettings("1", "11"), current)
        assertNotNull(original); assertNotNull(saved.attention); assertTrue(saved.session.exhausted)
    }
    @Test fun lockedWakeStorageFailurePreventsRestoreAndRetainsJournal() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000; diskFailed = true; val count = writes
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertEquals(count, writes); assertNotNull(original); assertFalse(c.state.value.running)
        assertNotNull(c.state.value.checkpoint.attention); assertTrue(saved.session.exhausted)
    }
    @Test fun freshLockedAllowanceSurvivesProcessRecoveryWithoutChargingGap() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); c.serviceGone(); now += 90_000
        val recovered = coordinator(); assertTrue(recovered.start(DeviceActivity.LOCKED, false))
        assertEquals(60_000L, saved.session.remaining(saved.config, now)); assertNull(saved.session.activeSince)
        recovered.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertEquals(0L, saved.session.consumedMs); assertEquals(now, saved.session.activeSince)
    }
    @Test fun recoveryPreservesConfirmedUsageWithoutChargingGap() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); now = 900_000
        val recovered = coordinator(); assertTrue(recovered.start(DeviceActivity.UNLOCKED, false))
        assertEquals(1000, recovered.state.value.checkpoint.session.consumedMs)
        assertTrue(recovered.state.value.checkpoint.interrupted); assertEquals(0, writes)
    }
    @Test fun pendingAppliedActivationDoesNotOverwriteSnapshot() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        saved = saved.copy(pending = DisplayEffect.ENABLE)
        val count = writes; val recovered = coordinator(); recovered.start(DeviceActivity.UNLOCKED, false)
        assertEquals(count, writes); assertEquals(ColorSettings("0", "12"), original)
    }
    @Test fun pendingActivationWithoutSnapshotCompletesAfterStartup() {
        saved = saved.copy(session = SessionState(true, true, 60_000, true), pending = DisplayEffect.ENABLE)
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, false); assertTrue(c.state.value.display.ownsGrayscale)
    }
    @Test fun disableRestoresAndCancelsObsoleteTimer() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); val generation = saved.session.generation
        expire(c); c.disable(); now += 90_000; c.event(SessionEvent.Deadline(generation))
        assertFalse(c.state.value.running); assertFalse(saved.session.enabled); assertNull(original); assertEquals(ColorSettings("0", "12"), current)
    }
    @Test fun failedActivationDoesNotClaimSuccessOrRetry() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); writeFailed = true; expire(c)
        assertFalse(c.state.value.display.ownsGrayscale); assertNotNull(c.state.value.checkpoint.attention)
        val count = writes; c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); assertEquals(count, writes)
    }
    @Test fun restorationFailureRetainsRecovery() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); permission = false; c.disable()
        assertNotNull(original); assertFalse(saved.session.enabled); assertNotNull(c.state.value.checkpoint.attention)
        permission = true; c.disable(); assertNull(original)
    }
    @Test fun storageFailureBeforeEffectPreventsWrites() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); diskFailed = true; expire(c)
        assertEquals(0, writes); assertFalse(c.state.value.running); assertNotNull(c.state.value.checkpoint.attention)
    }
    @Test fun disableRestorationRemainsAvailableDuringStorageFailure() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); diskFailed = true; c.disable()
        assertNull(original); assertEquals(ColorSettings("0", "12"), current)
    }
    @Test fun missingPermissionAndExistingCorrectionBlockStart() {
        val c = coordinator(); permission = false; assertFalse(c.start(DeviceActivity.UNLOCKED, true)); assertEquals(0, writes)
        permission = true; current = ColorSettings("1", "11"); assertFalse(c.start(DeviceActivity.UNLOCKED, true)); assertEquals(0, writes)
    }
    @Test fun rebootClearsAnchorsAndRestoresWithoutStartingService() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        val reboot = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) { saved = checkpoint }
        }) { 0 }; reboot.initialize(2)
        assertFalse(saved.session.enabled); assertNull(saved.session.activeSince); assertNull(original); assertFalse(reboot.state.value.running)
    }
    @Test fun confirmedUserStopNeverResumes() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        val stopped = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) { saved = checkpoint }
        }) { now }; stopped.initialize(1, true)
        assertFalse(stopped.start(DeviceActivity.UNLOCKED, false)); assertNull(original); assertFalse(saved.session.enabled)
    }
    @Test fun startupFailureLeavesDisabledRecoverableState() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); c.startupFailed("foreground denied")
        assertFalse(saved.session.enabled); assertNull(original); assertNotNull(c.state.value.checkpoint.attention)
    }
    @Test fun externalChangeIsNotOverwritten() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); current = ColorSettings("1", "11")
        val count = writes; c.inspectDisplay(); c.disable(); assertEquals(count, writes); assertNotNull(original)
    }
    @Test fun lateStartupAcknowledgmentCannotUndoDisable() {
        val c = coordinator(); val token = c.requestStart()!!; assertTrue(c.state.value.starting)
        c.disable(); assertFalse(c.start(DeviceActivity.UNLOCKED, true, token)); assertFalse(c.state.value.running)
    }
    @Test fun duplicateStartDoesNotResetConsumedUsage() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED)); c.start(DeviceActivity.UNLOCKED, true)
        assertEquals(1000, c.state.value.checkpoint.session.consumedMs)
    }
    @Test fun unreconciledDisplayBlocksRecoveryBeforeAnyWrite() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); current = ColorSettings("1", "11")
        val recovered = coordinator(); assertFalse(recovered.start(DeviceActivity.UNLOCKED, false)); assertEquals(0, writes)
        assertNotNull(recovered.state.value.checkpoint.attention)
    }
    @Test fun concurrentManualAndAutomaticCommandsShareOwnership() {
        val c = coordinator()
        val threads = (1..20).map { Thread { c.manualEnable(); c.start(DeviceActivity.UNLOCKED, true); c.manualEnable() } }
        threads.forEach(Thread::start); threads.forEach(Thread::join)
        assertTrue(c.state.value.running); assertNull(original); assertEquals(ColorSettings("0", "12"), current)
    }
    @Test fun repeatedServiceAcknowledgmentDoesNotStopOrResetSession() {
        val c = coordinator(); val token = c.requestStart()!!
        assertTrue(c.start(DeviceActivity.UNLOCKED, true, token)); now = 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertTrue(c.start(DeviceActivity.UNLOCKED, true, token)); assertTrue(c.start(DeviceActivity.UNLOCKED, false))
        assertEquals(1000, saved.session.consumedMs)
    }
    @Test fun persistenceFailureAfterActivationRetainsOriginalJournal() {
        val local = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) {
                check(!(current == ColorSettings("1", "0") && checkpoint.pending == null))
                saved = checkpoint
            }
        }) { now }
        local.initialize(1); local.start(DeviceActivity.UNLOCKED, true); expire(local)
        assertFalse(local.state.value.running); assertNotNull(original); assertEquals(DisplayEffect.ENABLE, saved.pending)
        val recovered = coordinator(); recovered.start(DeviceActivity.UNLOCKED, false)
        assertTrue(recovered.state.value.display.ownsGrayscale); assertEquals(ColorSettings("0", "12"), original)
    }
    @Test fun pendingRestoreCompletesWithoutNewActivation() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        saved = saved.copy(session = SessionState(), pending = DisplayEffect.RESTORE)
        val recovered = coordinator(); assertNull(original); assertFalse(recovered.state.value.running)
        assertNull(saved.pending)
    }
    @Test fun unknownBootIdentityInvalidatesInsteadOfComparingTimestamps() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        val recovered = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) { saved = checkpoint }
        }) { now }; recovered.initialize(null)
        assertFalse(saved.session.enabled); assertNull(original)
    }
    @Test fun failedLongBreakRestoreDoesNotClaimColorOrResumeTiming() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c)
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now += 60_000; permission = false
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertNotNull(c.state.value.checkpoint.attention); assertTrue(c.state.value.display.ownsGrayscale)
        assertTrue(saved.session.exhausted); assertEquals(60_000L, saved.session.consumedMs)
        assertNull(saved.session.activeSince); assertFalse(saved.session.resetEarned)
        val count = writes; now += 100_000; c.event(SessionEvent.Deadline(saved.session.generation)); assertEquals(count, writes)
    }
    @Test fun explicitResumeAfterInterruptionPreservesConfirmedUsage() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 1000; c.serviceGone()
        now = 900_000; val recovered = coordinator(); recovered.start(DeviceActivity.UNLOCKED, true)
        assertEquals(1000, saved.session.consumedMs); assertTrue(saved.interrupted)
    }
    @Test fun explicitResumeDoesNotRestoreOwnedGrayscale() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); c.serviceGone()
        val count = writes; val recovered = coordinator(); recovered.start(DeviceActivity.UNLOCKED, true)
        assertEquals(count, writes); assertTrue(recovered.state.value.display.ownsGrayscale)
    }
    @Test fun exhaustedWhileOffCanRecoverWithoutPretendingGrayscaleWasApplied() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 60_000
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); c.inspectDisplay()
        assertNull(saved.attention); assertEquals(0, writes); assertTrue(saved.session.exhausted)
        now += 1000; val recovered = coordinator(); assertTrue(recovered.start(DeviceActivity.OFF, false))
        assertEquals(0, writes); recovered.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertTrue(recovered.state.value.display.ownsGrayscale)
    }
    @Test fun revokedPermissionWhileOwnedIsReportedBeforeAnyFurtherWrite() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); expire(c); permission = false
        val count = writes; c.inspectDisplay(); assertNotNull(saved.attention); assertEquals(count, writes)
    }
    @Test fun corruptCheckpointStillAllowsDisplayRecovery() {
        controller.enable()
        val c = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load(): MonitoringCheckpoint = error("corrupt")
            override fun save(checkpoint: MonitoringCheckpoint) = error("corrupt")
        }) { now }
        c.initialize(1); assertFalse(c.start(DeviceActivity.UNLOCKED, true)); c.disable()
        assertNull(original); assertEquals(ColorSettings("0", "12"), current)
    }
    @Test fun missedWakeBoundaryDoesNotJoinTwoOffIntervals() {
        val c = coordinator(); c.start(DeviceActivity.UNLOCKED, true); now = 1000
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); now = 100_000
        c.event(SessionEvent.Recover(DeviceActivity.OFF)); now += 1000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertEquals(1000, saved.session.consumedMs); assertTrue(saved.interrupted); assertFalse(saved.session.resetEarned)
    }
}
