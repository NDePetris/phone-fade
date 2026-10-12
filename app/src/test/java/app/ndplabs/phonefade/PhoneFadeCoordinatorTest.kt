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
