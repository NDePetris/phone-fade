package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class CountdownFormatterTest {
    @Test fun minuteRoundingAndSecondsBoundaries() {
        for ((ms, expected) in listOf(120_000L to "2 minutes remaining", 119_999L to "2 minutes remaining",
            60_001L to "2 minutes remaining", 60_000L to "1 minute remaining", 59_999L to "0:59",
            59_000L to "0:59", 58_001L to "0:59", 1000L to "0:01", 1L to "0:01",
            0L to "Time exhausted", -1L to "Time exhausted")) assertEquals(expected, CountdownFormatter.remaining(ms))
    }
    @Test fun notificationDoesNotExposeStaleSeconds() {
        assertEquals("Less than a minute remaining", CountdownFormatter.remaining(23_001, true))
    }
    @Test fun positiveTimeNeverDisplaysZero() {
        for (ms in 1L..60_000L) assertNotEquals("0:00", CountdownFormatter.remaining(ms))
    }
    @Test fun refreshUsesNextPresentationBoundary() {
        assertEquals(60_000L, CountdownFormatter.nextRefresh(120_000))
        assertEquals(59_999L, CountdownFormatter.nextRefresh(119_999))
        assertEquals(1L, CountdownFormatter.nextRefresh(60_000))
        assertEquals(1L, CountdownFormatter.nextRefresh(58_001))
    }
    @Test fun pendingAndUnverifiedEffectsNeverClaimGrayscale() {
        val state = MonitoringState(MonitoringCheckpoint(session = SessionState(enabled = true, hasSession = true,
            consumedMs = 60_000, exhausted = true, activity = DeviceActivity.UNLOCKED)), running = true)
        assertEquals("Applying grayscale…", MonitoringPresentation.text(state, 100))
        assertFalse(MonitoringPresentation.canUseTemporaryColor(state))
        val pending = state.copy(checkpoint = state.checkpoint.copy(pending = DisplayEffect.RESTORE,
            pendingPurpose = OperationPurpose.TEMPORARY_COLOR))
        assertEquals("Restoring color…", MonitoringPresentation.text(pending, 100))
    }
}
