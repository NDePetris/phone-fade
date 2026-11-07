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

    /** Frozen v2 wire layout: do not use the current encoder to fabricate migration evidence. */
    private fun v2(value: MonitoringCheckpoint): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun temporary(item: TemporaryColorState?) {
                out.writeBoolean(item != null)
                item?.let { out.writeLong(it.durationMs); out.writeLong(it.consumedMs); out.writeLong(it.activeSince ?: -1) }
            }
            fun session(item: SessionState) = with(item) {
                out.writeBoolean(enabled); out.writeBoolean(hasSession); out.writeLong(consumedMs)
                out.writeBoolean(exhausted); out.writeUTF(activity.name)
                out.writeLong(activeSince ?: -1); out.writeLong(breakSince ?: -1)
                out.writeBoolean(resetEarned); out.writeLong(generation); out.writeLong(observedAt)
            }
            out.writeInt(2); out.writeInt(value.config.allowanceMinutes); out.writeInt(value.config.resetMinutes)
            out.writeInt(value.bootCount ?: -1); session(value.session)
            out.writeUTF(value.pending?.name ?: ""); out.writeBoolean(value.interrupted)
            out.writeBoolean(value.attention != null); value.attention?.let(out::writeUTF)
            out.writeInt(value.config.temporaryMinutes); temporary(value.session.temporary)
            out.writeUTF(value.pendingPurpose?.name ?: ""); out.writeBoolean(value.pendingTarget != null)
            value.pendingTarget?.let { session(it); temporary(it.temporary) }
            out.writeBoolean(value.expectedBaseline != null)
            value.expectedBaseline?.let { for (item in listOf(it.enabled, it.mode)) {
                out.writeBoolean(item != null); item?.let(out::writeUTF)
            } }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
    @Test fun v2MigrationPreservesConfirmedRemainderAtLastObservationAndAllOtherState() {
        for (activity in DeviceActivity.entries) {
            val cp = active().let { it.copy(session = it.session.copy(activity = activity, observedAt = 1000,
                temporary = TemporaryColorState(180_000, 20_000, if (activity == DeviceActivity.UNLOCKED) 900 else null))) }
            val expectedConsumed = if (activity == DeviceActivity.UNLOCKED) 20_100L else 20_000L
            val migrated = CheckpointCodec.decode(v2(cp))
            assertEquals(cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, expectedConsumed, 1000))), migrated)
            assertEquals(1000 + 180_000 - expectedConsumed, migrated.session.temporary!!.deadlineElapsed)
            assertEquals(migrated, CheckpointCodec.decode(CheckpointCodec.encode(migrated)))
        }
    }
    @Test fun v2MigrationPreservesPendingEntryAndReactivationWithoutArmingEntry() {
        val cp = active()
        val entry = cp.copy(session = cp.session.copy(temporary = null), pending = DisplayEffect.RESTORE,
            pendingPurpose = OperationPurpose.TEMPORARY_COLOR,
            pendingTarget = cp.session.copy(temporary = TemporaryColorState(180_000)))
        assertEquals(entry, CheckpointCodec.decode(v2(entry)))
        val expired = cp.session.copy(activity = DeviceActivity.LOCKED, temporary = TemporaryColorState(180_000, 180_000))
        val reactivation = cp.copy(session = expired, pending = DisplayEffect.ENABLE,
            pendingPurpose = OperationPurpose.REACTIVATE, pendingTarget = expired.copy(temporary = null))
        val migrated = CheckpointCodec.decode(v2(reactivation))
        assertEquals(reactivation.copy(session = expired.copy(temporary = TemporaryColorState(180_000, 180_000, 100))), migrated)
        assertEquals(migrated, CheckpointCodec.decode(CheckpointCodec.encode(migrated)))
    }
    @Test fun v2WithoutTemporaryColorHasNoBehaviorOrSettingsMigration() {
        val cp = active().copy(session = SessionState(enabled = true, hasSession = true, activity = DeviceActivity.OFF,
            consumedMs = 1234, breakSince = 100, observedAt = 200), expectedBaseline = null)
        assertEquals(cp, CheckpointCodec.decode(v2(cp)))
    }
    @Test fun v3RoundTripActiveOffAndExpiredOverrideIncludingAbsentBaseline() {
        val cp = active()
        for (value in listOf(cp, cp.copy(session = cp.session.copy(activity = DeviceActivity.OFF,
            breakSince = 100)),
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, 180_000, 100))))) {
            assertEquals(value, CheckpointCodec.decode(CheckpointCodec.encode(value)))
        }
    }
    @Test fun v3RoundTripPendingEntryAndReactivation() {
        val cp = active()
        val entry = cp.copy(session = cp.session.copy(temporary = null), pending = DisplayEffect.RESTORE,
            pendingPurpose = OperationPurpose.TEMPORARY_COLOR,
            pendingTarget = cp.session.copy(temporary = TemporaryColorState(180_000)))
        val reactivate = cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000, 180_000, 100)),
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
            cp.copy(session = cp.session.copy(temporary = TemporaryColorState(180_000))),
            cp.copy(pending = DisplayEffect.RESTORE),
            cp.copy(pending = DisplayEffect.ENABLE, pendingPurpose = OperationPurpose.TEMPORARY_COLOR, pendingTarget = cp.session),
            cp.copy(pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.TEMPORARY_COLOR))
        for (value in invalid) {
            try { CheckpointCodec.decode(CheckpointCodec.encode(value)); fail("Accepted invalid checkpoint: $value") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun unknownVersionAndTrailingDataAreRejected() {
        for (raw in listOf("AAAABA==", Base64.getEncoder().encodeToString(
            Base64.getDecoder().decode(CheckpointCodec.encode(active())) + byteArrayOf(0)))) {
            try { CheckpointCodec.decode(raw); fail() } catch (_: IllegalStateException) { }
        }
    }
}
