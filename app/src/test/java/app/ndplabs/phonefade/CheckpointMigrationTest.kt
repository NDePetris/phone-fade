package app.ndplabs.phonefade

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class CheckpointMigrationTest {
    /** Frozen v1 wire layout, intentionally independent of the current encoder. */
    private fun legacy(pending: DisplayEffect? = null, enabled: Boolean = true): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(1); it.writeInt(10); it.writeInt(5); it.writeInt(9)
            it.writeBoolean(enabled); it.writeBoolean(true); it.writeLong(600_000)
            it.writeBoolean(true); it.writeUTF("OFF"); it.writeLong(-1); it.writeLong(if (enabled) 100 else -1)
            it.writeBoolean(false); it.writeLong(4); it.writeLong(100)
            it.writeUTF(pending?.name ?: ""); it.writeBoolean(true); it.writeBoolean(false)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
    @Test fun v1PreservesConfigExhaustionUsageAndInterruption() {
        val cp = CheckpointCodec.decode(legacy())
        assertEquals(MonitoringConfig(10, 5, 5), cp.config)
        assertEquals(600_000L, cp.session.consumedMs); assertTrue(cp.session.exhausted)
        assertTrue(cp.interrupted); assertEquals(9, cp.bootCount)
        assertNull(cp.session.temporary); assertNull(cp.expectedBaseline)
        assertEquals(cp, CheckpointCodec.decode(CheckpointCodec.encode(cp)))
    }
    @Test fun v1PendingEffectsMigrateWithExplicitPurposeAndTarget() {
        for ((effect, enabled, purpose) in listOf(Triple(DisplayEffect.ENABLE, true, OperationPurpose.ACTIVATE),
            Triple(DisplayEffect.RESTORE, true, OperationPurpose.RESET), Triple(DisplayEffect.RESTORE, false, OperationPurpose.DISABLE))) {
            val cp = CheckpointCodec.decode(legacy(effect, enabled))
            assertEquals(effect, cp.pending); assertEquals(purpose, cp.pendingPurpose); assertEquals(cp.session, cp.pendingTarget)
            assertEquals(cp, CheckpointCodec.decode(CheckpointCodec.encode(cp)))
        }
    }
    private fun active() = MonitoringCheckpoint(config = MonitoringConfig(1, 2, 3), bootCount = 1,
        session = SessionState(enabled = true, hasSession = true, consumedMs = 60_000, exhausted = true,
            activity = DeviceActivity.UNLOCKED, observedAt = 100,
            temporary = TemporaryColorState(180_000, 20, 100)), expectedBaseline = ColorSettings(null, null))
    @Test fun v2RoundTripActivePausedAndExpiredOverrideIncludingAbsentBaseline() {
        val cp = active()
        for (value in listOf(cp, cp.copy(session = cp.session.copy(activity = DeviceActivity.OFF,
            breakSince = 100, temporary = cp.session.temporary!!.copy(activeSince = null))),
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, 180_000))))) {
            assertEquals(value, CheckpointCodec.decode(CheckpointCodec.encode(value)))
        }
    }
    @Test fun v2RoundTripPendingEntryAndReactivation() {
        val cp = active()
        val entry = cp.copy(session = cp.session.copy(temporary = null), pending = DisplayEffect.RESTORE,
            pendingPurpose = OperationPurpose.TEMPORARY_COLOR, pendingTarget = cp.session)
        val reactivate = cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, 180_000)),
            pending = DisplayEffect.ENABLE, pendingPurpose = OperationPurpose.REACTIVATE,
            pendingTarget = cp.session.copy(temporary = null))
        for (value in listOf(entry, reactivate)) assertEquals(value, CheckpointCodec.decode(CheckpointCodec.encode(value)))
    }
    @Test fun invalidRelationshipsAndMissingBaselineAreRejected() {
        val cp = active()
        val invalid = listOf(cp.copy(expectedBaseline = null), cp.copy(expectedBaseline = ColorSettings("1", "0")),
            cp.copy(session = cp.session.copy(exhausted = false)),
            cp.copy(session = cp.session.copy(activeSince = 100)),
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(1))),
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, -1))),
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, 0, 101))),
            cp.copy(pending = DisplayEffect.RESTORE),
            cp.copy(pending = DisplayEffect.ENABLE, pendingPurpose = OperationPurpose.TEMPORARY_COLOR, pendingTarget = cp.session),
            cp.copy(pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.TEMPORARY_COLOR))
        for (value in invalid) {
            try { CheckpointCodec.decode(CheckpointCodec.encode(value)); fail("Accepted invalid checkpoint: $value") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun unknownVersionAndTrailingDataAreRejected() {
        for (raw in listOf("AAAAAw==", Base64.getEncoder().encodeToString(
            Base64.getDecoder().decode(CheckpointCodec.encode(active())) + byteArrayOf(0)))) {
            try { CheckpointCodec.decode(raw); fail() } catch (_: IllegalStateException) { }
        }
    }
}
