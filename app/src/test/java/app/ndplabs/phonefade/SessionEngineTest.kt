package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class SessionEngineTest {
    private var config = MonitoringConfig(5, 5)
    private var state = SessionState()
    private var effect: DisplayEffect? = null
    private fun at(now: Long, event: SessionEvent) {
        val result = SessionEngine.reduce(state, event, now, config)
        state = result.state; effect = result.effect
    }
    private fun unlock(now: Long) = at(now, SessionEvent.Observe(DeviceActivity.UNLOCKED))
    private fun off(now: Long) = at(now, SessionEvent.Observe(DeviceActivity.OFF))
    private fun on(now: Long) = at(now, SessionEvent.Observe(DeviceActivity.LOCKED))
    private fun start() = at(0, SessionEvent.Enable(DeviceActivity.UNLOCKED))

    @Test fun firstUnlockGetsFullAllowance() { start(); assertEquals(300_000, state.remaining(config, 0)); assertTrue(state.hasSession) }
    @Test fun enablingLockedWaitsForUnlock() { at(0, SessionEvent.Enable(DeviceActivity.OFF)); assertFalse(state.hasSession); unlock(20); assertEquals(300_000, state.remaining(config, 20)) }
    @Test fun delayedDeadlineUsesElapsedTime() { start(); at(400_000, SessionEvent.Deadline(state.generation)); assertTrue(state.exhausted); assertEquals(DisplayEffect.ENABLE, effect) }
    @Test fun shortBreakPreservesUsage() { start(); off(180_000); on(300_000); unlock(310_000); assertEquals(120_000, state.remaining(config, 310_000)) }
    @Test fun offTimeNeverConsumesUsage() { start(); off(40_000); at(500_000, SessionEvent.Deadline(state.generation)); assertEquals(40_000, state.consumedMs); assertNull(effect) }
    @Test fun shortBreakPreservesExhaustion() { start(); at(300_000, SessionEvent.Deadline(state.generation)); off(310_000); unlock(400_000); assertTrue(state.exhausted); assertEquals(DisplayEffect.ENABLE, effect) }
    @Test fun exactResetRestoresAndResets() { start(); off(100_000); on(400_000); assertEquals(DisplayEffect.RESTORE, effect); assertEquals(0, state.consumedMs); unlock(410_000); assertNull(effect); assertEquals(0, state.consumedMs) }
    @Test fun exhaustedLongBreakRestores() { start(); at(300_000, SessionEvent.Deadline(state.generation)); off(310_000); unlock(700_000); assertFalse(state.exhausted); assertEquals(DisplayEffect.RESTORE, effect) }
    @Test fun awakeLockScreenDoesNotExtendBreak() { start(); off(100_000); on(200_000); unlock(900_000); assertFalse(state.resetEarned); assertEquals(100_000, state.consumedMs) }
    @Test fun separateShortBreaksDoNotCombine() { start(); off(100_000); on(300_000); off(310_000); unlock(510_000); assertEquals(100_000, state.consumedMs) }
    @Test fun freshAllowanceSurvivesAnotherShortBreakBeforeUnlock() { start(); off(100_000); on(400_000); assertEquals(DisplayEffect.RESTORE, effect); off(410_000); unlock(420_000); assertNull(effect); assertEquals(300_000, state.remaining(config, 420_000)) }
    @Test fun qualifyingWakeWaitsIndefinitelyWithoutChargingKeyguard() {
        start(); off(100_000); on(400_000)
        assertNull(state.activeSince); assertNull(state.deadline(config)); assertNull(state.breakSince)
        on(900_000); assertNull(effect); assertEquals(300_000, state.remaining(config, 900_000))
        unlock(1_000_000); assertEquals(1_000_000L, state.activeSince)
        unlock(1_000_500); assertEquals(500L, state.consumedMs)
    }
    @Test fun repeatedLockScreenWakesInterruptReset() {
        start(); at(300_000, SessionEvent.Deadline(state.generation)); off(310_000)
        repeat(4) { i -> on(510_000 + i * 210_000L); assertNull(effect); off(520_000 + i * 210_000L) }
        unlock(1_200_000); assertTrue(state.exhausted); assertFalse(state.resetEarned)
    }
    @Test fun obsoleteReconciliationCannotEraseNewerUnlock() {
        start(); unlock(1000); at(500, SessionEvent.Recover(DeviceActivity.OFF))
        assertEquals(DeviceActivity.UNLOCKED, state.activity); assertEquals(1000L, state.consumedMs)
    }
    @Test fun legacyEarnedResetRestoresWhileLocked() {
        state = SessionState(enabled = true, hasSession = true, consumedMs = 300_000,
            exhausted = true, resetEarned = true)
        on(1000); assertEquals(DisplayEffect.RESTORE, effect); assertFalse(state.resetEarned)
        assertEquals(300_000L, state.remaining(config, 1000)); assertNull(state.activeSince)
    }
    @Test fun rapidCyclesAccumulateOnlyActiveTime() { start(); repeat(100) { i -> off(i * 1000L + 500); unlock(i * 1000L + 1000) }; assertEquals(50_000, state.consumedMs) }
    @Test fun duplicateOffKeepsOriginalAnchor() { start(); off(100); off(200); assertEquals(100L, state.breakSince) }
    @Test fun duplicateUnlockDoesNotResetUsage() { start(); unlock(100); unlock(200); assertEquals(200, state.consumedMs) }
    @Test fun lateObsoleteDeadlineIgnored() { start(); val generation = state.generation; off(100); at(400_000, SessionEvent.Deadline(generation)); assertFalse(state.exhausted); assertEquals(100, state.consumedMs) }
    @Test fun expirationDuringOffIsDeferredUntilUnlock() { start(); off(310_000); assertTrue(state.exhausted); assertNull(effect); unlock(320_000); assertEquals(DisplayEffect.ENABLE, effect) }
    @Test fun lowerAllowancePreservesConsumption() { start(); config = MonitoringConfig(1, 5); at(70_000, SessionEvent.ConfigurationChanged); assertTrue(state.exhausted) }
    @Test fun increaseDoesNotUnlatchExhaustion() { start(); at(300_000, SessionEvent.Deadline(state.generation)); config = MonitoringConfig(10, 5); at(310_000, SessionEvent.ConfigurationChanged); assertTrue(state.exhausted) }
    @Test fun resetChangeAppliesToCurrentBreak() { start(); off(100); config = MonitoringConfig(5, 1); at(200, SessionEvent.ConfigurationChanged); unlock(60_100); assertEquals(DisplayEffect.RESTORE, effect) }
    @Test fun disableAndReenableStartClean() { start(); off(100); at(200, SessionEvent.Disable); assertEquals(DisplayEffect.RESTORE, effect); assertNull(state.deadline(config)); at(300, SessionEvent.Enable(DeviceActivity.UNLOCKED)); assertEquals(0, state.consumedMs) }
    @Test fun recoveryDoesNotChargeDowntimeOrInferBreak() { start(); off(100); at(900_000, SessionEvent.Recover(DeviceActivity.UNLOCKED)); assertEquals(100, state.consumedMs); assertNull(effect); assertEquals(900_000L, state.activeSince) }
    @Test fun activeRecoveryKeepsOnlyCheckpointUsage() { start(); unlock(100); at(900_000, SessionEvent.Recover(DeviceActivity.UNLOCKED)); assertEquals(100, state.consumedMs) }
    @Test fun obsoleteObservationIgnored() { start(); off(200); unlock(100); assertEquals(DeviceActivity.OFF, state.activity) }
    @Test fun rangeEnforced() { for (n in listOf(0, 121)) { try { MonitoringConfig(n, 5); fail() } catch (_: IllegalArgumentException) {} } }
}
