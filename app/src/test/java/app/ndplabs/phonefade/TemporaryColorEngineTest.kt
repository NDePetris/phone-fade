package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class TemporaryColorEngineTest {
    private var config = MonitoringConfig(1, 2, 1)
    private var state = SessionState(enabled = true, hasSession = true, consumedMs = 60_000, exhausted = true,
        activity = DeviceActivity.UNLOCKED)
    private fun event(event: SessionEvent, now: Long) = SessionEngine.reduce(state, event, now, config).also { state = it.state }
    private fun begin() {
        assertEquals(OperationPurpose.TEMPORARY_COLOR, event(SessionEvent.RequestTemporaryColor, 0).purpose)
        // Coordinator starts the anchor only after verified restoration + durable completion.
        state = state.copy(temporary = state.temporary!!.copy(activeSince = 0))
    }
    @Test fun countsUnlockedTimeWithoutResettingOrdinarySession() {
        begin(); event(SessionEvent.Deadline(state.generation), 20_000)
        assertEquals(40_000L, state.temporary!!.remaining(20_000)); assertEquals(60_000L, state.consumedMs)
        assertTrue(state.exhausted)
    }
    @Test fun shortOffAndLockScreenPauseThenResume() {
        begin(); event(SessionEvent.Observe(DeviceActivity.OFF), 20_000)
        event(SessionEvent.Observe(DeviceActivity.LOCKED), 30_000)
        assertEquals(40_000L, state.temporary!!.remaining(300_000)); assertNull(state.deadline(config))
        event(SessionEvent.Observe(DeviceActivity.UNLOCKED), 300_000)
        assertEquals(340_000L, state.deadline(config)); assertTrue(state.exhausted)
    }
    @Test fun qualifyingWakeEndsOverrideBeforeUnlock() {
        begin(); event(SessionEvent.Observe(DeviceActivity.OFF), 20_000)
        val reset = event(SessionEvent.Observe(DeviceActivity.LOCKED), 140_000)
        assertEquals(OperationPurpose.RESET, reset.purpose); assertNull(state.temporary)
        assertEquals(0L, state.consumedMs); assertFalse(state.exhausted); assertNull(state.activeSince)
    }
    @Test fun separateShortBreaksDoNotReset() {
        begin()
        repeat(3) { i ->
            event(SessionEvent.Observe(DeviceActivity.OFF), i * 100_000L + 1000)
            event(SessionEvent.Observe(DeviceActivity.LOCKED), i * 100_000L + 90_000)
        }
        assertTrue(state.exhausted); assertNotNull(state.temporary)
    }
    @Test fun expiryUsesMonotonicElapsedTimeWhenCallbackLate() {
        begin(); val result = event(SessionEvent.Deadline(state.generation), 90_000)
        assertEquals(OperationPurpose.REACTIVATE, result.purpose); assertEquals(DisplayEffect.ENABLE, result.effect)
        assertNull(state.temporary); assertTrue(state.exhausted); assertEquals(60_000L, state.consumedMs)
    }
    @Test fun expiryDiscoveredOffDefersUntilUnlock() {
        begin(); assertNull(event(SessionEvent.Observe(DeviceActivity.OFF), 60_000).effect)
        assertEquals(0L, state.temporary!!.remaining(90_000))
        assertNull(event(SessionEvent.Observe(DeviceActivity.LOCKED), 90_000).effect)
        assertEquals(OperationPurpose.REACTIVATE, event(SessionEvent.Observe(DeviceActivity.UNLOCKED), 95_000).purpose)
    }
    @Test fun qualifyingBreakWinsOverExpiredOverride() {
        begin(); event(SessionEvent.Observe(DeviceActivity.OFF), 60_000)
        assertEquals(OperationPurpose.RESET, event(SessionEvent.Observe(DeviceActivity.UNLOCKED), 180_000).purpose)
        assertFalse(state.exhausted)
    }
    @Test fun durationChangeAndAllowanceIncreaseDoNotExtendOverride() {
        begin(); config = MonitoringConfig(120, 2, 120)
        event(SessionEvent.ConfigurationChanged, 20_000)
        assertEquals(60_000L, state.temporary!!.durationMs); assertTrue(state.exhausted)
        assertEquals(OperationPurpose.REACTIVATE, event(SessionEvent.Deadline(state.generation), 60_000).purpose)
    }
    @Test fun repeatedRequestCannotStackOrExtend() {
        begin(); val previous = state
        assertNull(event(SessionEvent.RequestTemporaryColor, 20_000).effect); assertEquals(previous, state)
    }
    @Test fun recoveryDoesNotChargeDowntimeOrInferBreak() {
        begin(); event(SessionEvent.Deadline(state.generation), 20_000)
        event(SessionEvent.Recover(DeviceActivity.UNLOCKED), 900_000)
        assertEquals(40_000L, state.temporary!!.remaining(900_000)); assertTrue(state.exhausted)
        assertNull(state.breakSince)
    }
    @Test fun disableCancelsOverrideAndGenerationRejectsOldTimer() {
        begin(); val generation = state.generation
        event(SessionEvent.Disable, 20_000)
        assertNull(state.temporary); assertFalse(state.enabled)
        assertNull(event(SessionEvent.Deadline(generation), 60_000).effect)
    }
    @Test fun rangeAndUnavailableStates() {
        for (duration in listOf(0, 121)) {
            try { MonitoringConfig(1, 1, duration); fail() } catch (_: IllegalArgumentException) { }
        }
        for (candidate in listOf(SessionState(), state.copy(exhausted = false), state.copy(activity = DeviceActivity.LOCKED))) {
            assertNull(SessionEngine.reduce(candidate, SessionEvent.RequestTemporaryColor, 0, config).effect)
        }
    }
}
