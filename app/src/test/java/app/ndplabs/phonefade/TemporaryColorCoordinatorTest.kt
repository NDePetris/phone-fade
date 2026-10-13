package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class TemporaryColorCoordinatorTest {
    private class ProcessDeath : Error()
    private class Fixture(val baseline: ColorSettings = ColorSettings("0", "12")) {
        var now = 0L
        var current = baseline
        var original: ColorSettings? = null
        var saved = MonitoringCheckpoint(config = MonitoringConfig(1, 2, 1), bootCount = 1)
        var permission = true
        var journalSaveFails = false
        var journalClearFails = false
        var failCommit: (MonitoringCheckpoint) -> Boolean = { false }
        var writes = 0
        var dieAtWrite: Int? = null
        var rejectAtWrite: Int? = null
        var dieAfterJournalClear = false
        var writeDelayMs = 0L
        val controller = GrayscaleController(object : DisplaySettings {
            override fun hasWritePermission() = permission
            override fun read(key: String) = if (key == GrayscaleController.ENABLED) current.enabled else current.mode
            override fun write(key: String, value: String?): Boolean {
                writes++
                now += writeDelayMs
                if (writes == rejectAtWrite) return false
                current = if (key == GrayscaleController.ENABLED) current.copy(enabled = value) else current.copy(mode = value)
                if (writes == dieAtWrite) throw ProcessDeath()
                return true
            }
        }, object : RecoveryStore {
            override fun load() = original
            override fun save(original: ColorSettings) { check(!journalSaveFails); this@Fixture.original = original }
            override fun clear() {
                check(!journalClearFails)
                original = null
                if (dieAfterJournalClear) throw ProcessDeath()
            }
        })
        fun coordinator(boot: Int? = 1, userStopped: Boolean = false) = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) {
                check(!failCommit(checkpoint)) { "Injected commit failure" }
                saved = CheckpointCodec.decode(CheckpointCodec.encode(checkpoint))
            }
        }) { now }.also { it.initialize(boot, userStopped) }
        fun exhausted(): PhoneFadeCoordinator = coordinator().also {
            assertTrue(it.start(DeviceActivity.UNLOCKED, true))
            now = 60_000
            it.event(SessionEvent.Deadline(saved.session.generation))
            assertTrue(it.state.value.display.ownsGrayscale)
        }
        fun override(): PhoneFadeCoordinator = exhausted().also {
            it.temporaryColor()
            assertNotNull(it.state.value.checkpoint.session.temporary)
            assertEquals(baseline, current); assertNull(original)
        }
        fun expire(c: PhoneFadeCoordinator) {
            now += 60_000
            c.event(SessionEvent.Deadline(saved.session.generation))
        }
    }

    @Test fun entryAndExpiryPreserveExhaustionAndExactBaseline() {
        val f = Fixture(); val c = f.override(); val consumed = f.saved.session.consumedMs
        assertTrue(f.saved.session.exhausted); assertEquals(f.baseline, f.saved.expectedBaseline)
        f.expire(c)
        assertTrue(c.state.value.display.ownsGrayscale); assertEquals(f.baseline, f.original)
        assertNull(f.saved.session.temporary); assertEquals(consumed, f.saved.session.consumedMs)
        c.disable(); assertEquals(f.baseline, f.current); assertNull(f.original)
    }
    @Test fun absentKeysSurviveOverrideReactivationAndFinalRestore() {
        val f = Fixture(ColorSettings(null, null)); val c = f.override()
        f.expire(c); assertEquals(ColorSettings(null, null), f.original)
        c.disable(); assertEquals(ColorSettings(null, null), f.current)
    }
    @Test fun onlyAutomaticOwnedGrayscaleCanRequestOverride() {
        val f = Fixture(); val c = f.coordinator()
        c.manualEnable(); val count = f.writes; c.temporaryColor()
        assertNull(f.saved.session.temporary); assertEquals(count, f.writes)
        c.disable(); c.start(DeviceActivity.UNLOCKED, true); c.temporaryColor()
        assertNull(f.saved.session.temporary)
    }
    @Test fun repeatedRequestsAndDurationEditsCannotExtendActiveOverride() {
        val f = Fixture(); val c = f.override(); val count = f.writes
        f.now += 20_000; c.temporaryColor(); c.configure(MonitoringConfig(120, 2, 120))
        assertEquals(60_000L, f.saved.session.temporary!!.durationMs)
        assertEquals(40_000L, f.saved.session.temporary!!.remaining(f.now)); assertEquals(count, f.writes)
        assertTrue(f.saved.session.exhausted)
    }
    @Test fun shortBreakPausesOverrideAndQualifyingWakeResetsBeforeUnlock() {
        val f = Fixture(); val c = f.override(); f.now += 20_000
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); f.now += 20_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED)); f.now += 90_000
        assertEquals(40_000L, f.saved.session.temporary!!.remaining(f.now))
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); f.now += 120_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertNull(f.saved.session.temporary); assertFalse(f.saved.session.exhausted)
        assertEquals(60_000L, f.saved.session.remaining(f.saved.config, f.now)); assertNull(f.saved.session.activeSince)
    }
    @Test fun lockedExpiryWaitsForUnlock() {
        val f = Fixture(); val c = f.override(); f.now += 60_000
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertFalse(c.state.value.display.ownsGrayscale); assertEquals(0L, f.saved.session.temporary!!.remaining(f.now))
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertTrue(c.state.value.display.ownsGrayscale)
    }
    @Test fun interruptionDuringEachRestorationWriteRetainsSnapshotAndPurpose() {
        for (step in 1..3) {
            val f = Fixture(); val c = f.exhausted(); f.dieAtWrite = f.writes + step
            try { c.temporaryColor(); fail() } catch (_: ProcessDeath) { }
            assertEquals(f.baseline, f.original); assertNull(f.saved.session.temporary)
            assertEquals(OperationPurpose.TEMPORARY_COLOR, f.saved.pendingPurpose)
            assertTrue(f.saved.session.exhausted)
            f.dieAtWrite = null
            val recovered = f.coordinator()
            assertEquals(f.baseline, f.current); assertNull(f.original)
            assertNotNull(f.saved.session.temporary); assertNull(f.saved.session.temporary!!.activeSince)
            assertTrue(recovered.start(DeviceActivity.UNLOCKED, false))
            assertEquals(60_000L, f.saved.session.temporary!!.remaining(f.now))
        }
    }
    @Test fun deathAfterVerifiedRestoreAndJournalClearReconcilesBaseline() {
        val f = Fixture(); val c = f.exhausted(); f.dieAfterJournalClear = true
        try { c.temporaryColor(); fail() } catch (_: ProcessDeath) { }
        assertNull(f.original); assertEquals(f.baseline, f.current)
        f.dieAfterJournalClear = false; val recovered = f.coordinator()
        assertNotNull(f.saved.session.temporary); assertTrue(recovered.start(DeviceActivity.UNLOCKED, false))
        assertTrue(f.saved.session.exhausted)
    }
    @Test fun failedIntentPersistencePreventsRestoration() {
        val f = Fixture(); val c = f.exhausted(); val count = f.writes
        f.failCommit = { it.pendingPurpose == OperationPurpose.TEMPORARY_COLOR }
        c.temporaryColor(); assertEquals(count, f.writes); assertEquals(f.baseline, f.original)
        assertNull(c.state.value.checkpoint.session.temporary); assertFalse(c.state.value.running)
    }
    @Test fun restorationSuccessThenFailedCompletionNeverClaimsStartedOverride() {
        val f = Fixture(); val c = f.exhausted()
        f.failCommit = { it.pending == null && it.session.temporary != null }
        c.temporaryColor()
        assertEquals(f.baseline, f.current); assertNull(f.original)
        assertNull(c.state.value.checkpoint.session.temporary); assertNotNull(c.state.value.checkpoint.attention)
        assertEquals(OperationPurpose.TEMPORARY_COLOR, f.saved.pendingPurpose)
        f.failCommit = { false }; val recovered = f.coordinator()
        assertNotNull(f.saved.session.temporary); assertTrue(recovered.start(DeviceActivity.UNLOCKED, false))
        assertTrue(f.saved.session.exhausted)
    }
    @Test fun failedRestoreAndFailedJournalClearDoNotStartOverride() {
        for (step in 1..4) {
            val f = Fixture(); val c = f.exhausted()
            if (step == 4) f.journalClearFails = true else f.rejectAtWrite = f.writes + step
            c.temporaryColor()
            assertNull(c.state.value.checkpoint.session.temporary); assertNotNull(f.original)
            assertNotNull(c.state.value.checkpoint.attention); assertTrue(f.saved.session.exhausted)
            assertEquals(OperationPurpose.TEMPORARY_COLOR, f.saved.pendingPurpose)
            f.journalClearFails = false; f.rejectAtWrite = null; c.disable()
            assertNull(f.original); assertEquals(f.baseline, f.current)
        }
    }
    @Test fun externalChangesDuringOverrideBlockObserverAndExpiryWrites() {
        for (external in listOf(ColorSettings("1", "11"), ColorSettings("0", "11"), ColorSettings(null, "12"))) {
            val f = Fixture(); val c = f.override(); val count = f.writes
            f.current = external; f.expire(c)
            assertEquals(count, f.writes); assertEquals(external, f.current)
            assertNotNull(c.state.value.checkpoint.attention); assertFalse(c.state.value.display.ownsGrayscale)
            c.disable(); assertEquals(external, f.current)
        }
    }
    @Test fun externalChangeAfterInterruptedRestorationDoesNotCompleteOverride() {
        val f = Fixture(); val c = f.exhausted(); f.dieAfterJournalClear = true
        try { c.temporaryColor(); fail() } catch (_: ProcessDeath) { }
        f.dieAfterJournalClear = false; f.current = ColorSettings("0", "11"); val count = f.writes
        val recovered = f.coordinator()
        assertNull(recovered.state.value.checkpoint.session.temporary); assertNotNull(recovered.state.value.checkpoint.attention)
        assertEquals(count, f.writes); assertEquals(ColorSettings("0", "11"), f.current)
    }
    @Test fun interruptionDuringReactivationReconcilesOrRestoresWithoutReset() {
        for (step in 1..2) {
            val f = Fixture(); val c = f.override(); f.dieAtWrite = f.writes + step
            try { f.expire(c); fail() } catch (_: ProcessDeath) { }
            assertEquals(OperationPurpose.REACTIVATE, f.saved.pendingPurpose); assertEquals(f.baseline, f.original)
            f.dieAtWrite = null; val recovered = f.coordinator()
            assertTrue(f.saved.session.exhausted); assertEquals(60_000L, f.saved.session.consumedMs)
            if (step == 2) {
                assertTrue(recovered.state.value.display.ownsGrayscale); assertNull(f.saved.session.temporary)
            } else {
                assertEquals(f.baseline, f.current); assertNotNull(recovered.state.value.checkpoint.attention)
            }
        }
    }
    @Test fun failedReactivationCompletionRetainsJournalAndReconcilesWithoutNewWrites() {
        val f = Fixture(); val c = f.override()
        f.failCommit = { it.pending == null && it.session.temporary == null && f.original != null }
        f.expire(c); assertEquals(f.baseline, f.original)
        assertNotNull(c.state.value.checkpoint.attention); assertEquals(OperationPurpose.REACTIVATE, f.saved.pendingPurpose)
        val count = f.writes; f.failCommit = { false }; val recovered = f.coordinator()
        assertTrue(recovered.state.value.display.ownsGrayscale); assertEquals(count, f.writes)
        assertNull(f.saved.session.temporary); assertEquals(f.baseline, f.original)
    }
    @Test fun reactivationJournalSaveFailureStopsAndDoesNotClaimGrayscale() {
        val f = Fixture(); val c = f.override(); f.journalSaveFails = true; f.expire(c)
        assertEquals(f.baseline, f.current); assertFalse(c.state.value.display.ownsGrayscale)
        assertNotNull(c.state.value.checkpoint.attention); assertTrue(f.saved.session.exhausted)
    }
    @Test fun processRecoveryPreservesOnlyConfirmedOverrideUsage() {
        val f = Fixture(); val c = f.override(); f.now += 20_000
        c.event(SessionEvent.Deadline(f.saved.session.generation)); f.now += 900_000
        val recovered = f.coordinator(); assertTrue(recovered.start(DeviceActivity.UNLOCKED, false))
        assertEquals(40_000L, f.saved.session.temporary!!.remaining(f.now)); assertTrue(f.saved.interrupted)
    }
    @Test fun rebootAndUserStopCancelOverrideWithoutRestarting() {
        for (userStopped in listOf(false, true)) {
            val f = Fixture(); f.override()
            val recovered = f.coordinator(if (userStopped) 1 else 2, userStopped)
            assertNull(f.saved.session.temporary); assertFalse(f.saved.session.enabled)
            assertFalse(recovered.state.value.running); assertEquals(f.baseline, f.current)
        }
    }
    @Test fun permissionLossStopsOverrideAndObserverDetectsDisabledModeChange() {
        val f = Fixture(); val c = f.override(); f.permission = false; c.inspectDisplay()
        assertNotNull(c.state.value.checkpoint.attention)
        val second = Fixture(); val other = second.override(); second.current = ColorSettings("0", "11")
        other.inspectDisplay(); assertNotNull(other.state.value.checkpoint.attention)
    }
    @Test fun disableRemainsAvailableAfterCompletionStorageFailure() {
        val f = Fixture(); val c = f.override()
        f.failCommit = { it.pending == null && f.original != null }; f.expire(c)
        f.failCommit = { true }; c.disable()
        assertEquals(f.baseline, f.current); assertNull(f.original); assertFalse(c.state.value.running)
    }
    @Test fun pendingReactivationBeforeAnyWriteIsDeferredUntilUnlockedStartup() {
        val f = Fixture(); val c = f.override()
        f.failCommit = { it.pendingPurpose == OperationPurpose.REACTIVATE }
        f.expire(c)
        // The failed intent did not reach disk; simulate the last durable intent from
        // a process that died immediately after its successful pre-write commit.
        f.failCommit = { false }
        val expired = f.saved.session.copy(temporary = TemporaryColorState(60_000, 60_000))
        f.saved = f.saved.copy(session = expired, pending = DisplayEffect.ENABLE,
            pendingPurpose = OperationPurpose.REACTIVATE, pendingTarget = expired.copy(temporary = null))
        val count = f.writes; val recovered = f.coordinator()
        assertTrue(recovered.start(DeviceActivity.LOCKED, false)); assertEquals(count, f.writes)
        recovered.event(SessionEvent.Observe(DeviceActivity.UNLOCKED))
        assertTrue(recovered.state.value.display.ownsGrayscale); assertTrue(f.saved.session.exhausted)
    }
    @Test fun serviceGoneAccountsOnlyObservedOverrideUseAndClearsBreak() {
        val f = Fixture(); val c = f.override(); f.now += 20_000; c.serviceGone()
        assertEquals(20_000L, f.saved.session.temporary!!.consumedMs)
        assertNull(f.saved.session.temporary!!.activeSince); assertNull(f.saved.session.breakSince)
        assertFalse(c.state.value.running)
    }
    @Test fun resetFailureDuringOverrideCannotGrantFreshAllowance() {
        val f = Fixture(); val c = f.override()
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); f.now += 120_000
        f.current = ColorSettings("0", "11"); val count = f.writes
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertTrue(f.saved.session.exhausted); assertEquals(60_000L, f.saved.session.consumedMs)
        assertNotNull(f.saved.session.temporary); assertEquals(OperationPurpose.RESET, f.saved.pendingPurpose)
        assertEquals(count, f.writes); assertNotNull(c.state.value.checkpoint.attention)
    }
    @Test fun resetCompletionCommitFailurePreservesOrdinaryUsageOnDisk() {
        val f = Fixture(); val c = f.override()
        c.event(SessionEvent.Observe(DeviceActivity.OFF)); f.now += 120_000
        f.failCommit = { it.pending == null && !it.session.exhausted }
        c.event(SessionEvent.Observe(DeviceActivity.LOCKED))
        assertTrue(f.saved.session.exhausted); assertNotNull(f.saved.session.temporary)
        assertEquals(OperationPurpose.RESET, f.saved.pendingPurpose)
        assertNotNull(c.state.value.checkpoint.attention)
        f.failCommit = { false }; val recovered = f.coordinator()
        assertFalse(f.saved.session.exhausted); assertNull(f.saved.session.temporary)
        assertFalse(recovered.state.value.running); assertEquals(f.baseline, f.current)
    }
    @Test fun queuedOffAndUnlockDuringRestorationAreReconciledWithoutChargingGap() {
        val f = Fixture(); val c = f.exhausted(); val requestAt = f.now
        f.writeDelayMs = 1000; c.temporaryColor(); f.writeDelayMs = 0
        assertEquals(requestAt + 3000, f.now)
        c.event(SessionEvent.Observe(DeviceActivity.OFF), requestAt + 1000)
        assertEquals(DeviceActivity.OFF, f.saved.session.activity)
        assertNull(f.saved.session.temporary!!.activeSince)
        assertEquals(60_000L, f.saved.session.temporary!!.remaining(f.now))
        // Even a long processing gap cannot turn queued boundaries into an earned reset.
        f.now += 900_000
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED), requestAt + 2000)
        assertEquals(DeviceActivity.UNLOCKED, f.saved.session.activity)
        assertTrue(f.saved.session.exhausted); assertTrue(f.saved.interrupted)
        assertEquals(60_000L, f.saved.session.temporary!!.remaining(f.now))
        c.event(SessionEvent.Observe(DeviceActivity.OFF), requestAt + 1000)
        assertEquals(DeviceActivity.UNLOCKED, f.saved.session.activity) // older queued signal cannot undo unlock
        f.now += 1000
        c.event(SessionEvent.Observe(DeviceActivity.OFF), f.now)
        val activity = f.saved.session.activity
        c.event(SessionEvent.Observe(DeviceActivity.UNLOCKED), requestAt + 2000)
        assertEquals(activity, f.saved.session.activity) // genuinely obsolete after a newer observation
    }
    @Test fun disableAndReenableDoNotAcceptSignalsFromPreviousSession() {
        val f = Fixture(); val c = f.override(); f.expire(c); val oldObservation = f.now
        f.writeDelayMs = 1000; c.disable(); f.writeDelayMs = 0
        assertTrue(c.start(DeviceActivity.UNLOCKED, true))
        c.event(SessionEvent.Observe(DeviceActivity.OFF), oldObservation + 1000)
        assertEquals(DeviceActivity.UNLOCKED, f.saved.session.activity)
        assertFalse(f.saved.session.exhausted); assertNull(f.saved.session.breakSince)
    }
}
