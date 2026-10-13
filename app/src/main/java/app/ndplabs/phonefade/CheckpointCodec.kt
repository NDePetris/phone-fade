package app.ndplabs.phonefade

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** A single versioned record; strict decoding prevents fabricated anchors or silent resets. */
internal object CheckpointCodec {
    fun encode(value: MonitoringCheckpoint): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(2)
            out.writeInt(value.config.allowanceMinutes)
            out.writeInt(value.config.resetMinutes)
            out.writeInt(value.bootCount ?: -1)
            with(value.session) {
                out.writeBoolean(enabled); out.writeBoolean(hasSession); out.writeLong(consumedMs)
                out.writeBoolean(exhausted); out.writeUTF(activity.name)
                out.writeLong(activeSince ?: -1); out.writeLong(breakSince ?: -1)
                out.writeBoolean(resetEarned); out.writeLong(generation); out.writeLong(observedAt)
            }
            out.writeUTF(value.pending?.name ?: "")
            out.writeBoolean(value.interrupted)
            out.writeBoolean(value.attention != null)
            value.attention?.let { out.writeUTF(it) }
            out.writeInt(value.config.temporaryMinutes)
            writeTemporary(out, value.session.temporary)
            out.writeUTF(value.pendingPurpose?.name ?: "")
            out.writeBoolean(value.pendingTarget != null)
            value.pendingTarget?.let { writeSession(out, it) }
            out.writeBoolean(value.expectedBaseline != null)
            value.expectedBaseline?.let { baseline ->
                for (item in listOf(baseline.enabled, baseline.mode)) {
                    out.writeBoolean(item != null)
                    item?.let(out::writeUTF)
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(raw: String): MonitoringCheckpoint = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw))).use { input ->
        val version = input.readInt()
        check(version in 1..2) { "Unknown monitoring checkpoint version" }
        var config = MonitoringConfig(input.readInt(), input.readInt())
        val boot = input.readInt().also { check(it >= -1) }.takeIf { it >= 0 }
        fun anchor(): Long? = input.readLong().also { check(it >= -1) }.takeIf { it >= 0 }
        var session = SessionState(input.readBoolean(), input.readBoolean(), input.readLong(), input.readBoolean(),
            DeviceActivity.valueOf(input.readUTF()), anchor(), anchor(), input.readBoolean(), input.readLong(), input.readLong())
        check(session.consumedMs in 0..Long.MAX_VALUE / 2 && session.generation >= 0 && session.observedAt >= 0)
        check(session.activeSince == null || (session.enabled && session.hasSession && session.activity == DeviceActivity.UNLOCKED && session.activeSince <= session.observedAt))
        check(session.breakSince == null || (session.enabled && session.activity == DeviceActivity.OFF && session.breakSince <= session.observedAt))
        check(!session.exhausted || session.hasSession)
        val pending = input.readUTF().takeIf { it.isNotEmpty() }?.let { DisplayEffect.valueOf(it) }
        val interrupted = input.readBoolean()
        val attention = if (input.readBoolean()) input.readUTF() else null
        var purpose: OperationPurpose? = null
        var target: SessionState? = null
        var baseline: ColorSettings? = null
        if (version == 2) {
            config = config.copy(temporaryMinutes = input.readInt())
            session = session.copy(temporary = readTemporary(input))
            purpose = input.readUTF().takeIf { it.isNotEmpty() }?.let(OperationPurpose::valueOf)
            target = if (input.readBoolean()) readSession(input) else null
            baseline = if (input.readBoolean()) ColorSettings(
                if (input.readBoolean()) input.readUTF() else null,
                if (input.readBoolean()) input.readUTF() else null) else null
            check((pending == null) == (purpose == null)) { "Incomplete pending operation" }
            check((purpose == null) == (target == null)) { "Missing operation target" }
            check(purpose == null || pending == when (purpose) {
                OperationPurpose.ACTIVATE, OperationPurpose.REACTIVATE -> DisplayEffect.ENABLE
                else -> DisplayEffect.RESTORE
            })
        } else if (pending != null) {
            // V1 had already persisted its target before a display effect.
            purpose = if (pending == DisplayEffect.ENABLE) OperationPurpose.ACTIVATE
                else if (session.enabled) OperationPurpose.RESET else OperationPurpose.DISABLE
            target = session
        }
        validate(session)
        target?.let(::validate)
        check(baseline == null || (baseline.enabled in listOf(null, "0") &&
            (baseline.mode == null || baseline.mode.matches(Regex("-?[0-9]+")))))
        check((session.temporary == null && purpose !in listOf(OperationPurpose.TEMPORARY_COLOR, OperationPurpose.REACTIVATE)) || baseline != null)
        if (purpose == OperationPurpose.TEMPORARY_COLOR) {
            check(session.enabled && session.exhausted && session.temporary == null)
            check(target != null && target.enabled && target.exhausted && target.temporary != null &&
                target.consumedMs == session.consumedMs)
        }
        if (purpose == OperationPurpose.REACTIVATE) {
            check(session.temporary != null && session.temporary.consumedMs >= session.temporary.durationMs)
            check(target != null && target.enabled && target.exhausted && target.temporary == null &&
                target.consumedMs == session.consumedMs)
        }
        check(input.available() == 0) { "Trailing checkpoint data" }
        MonitoringCheckpoint(config, session, boot, pending, attention, interrupted, purpose, target, baseline)
    }

    private fun writeTemporary(out: DataOutputStream, temporary: TemporaryColorState?) {
        out.writeBoolean(temporary != null)
        temporary?.let { out.writeLong(it.durationMs); out.writeLong(it.consumedMs); out.writeLong(it.activeSince ?: -1) }
    }

    private fun readTemporary(input: DataInputStream): TemporaryColorState? {
        if (!input.readBoolean()) return null
        val duration = input.readLong()
        val consumed = input.readLong()
        val anchor = input.readLong().also { check(it >= -1) }.takeIf { it >= 0 }
        check(duration in 60_000..7_200_000 && duration % 60_000 == 0L && consumed in 0..Long.MAX_VALUE / 2)
        return TemporaryColorState(duration, consumed, anchor)
    }

    private fun writeSession(out: DataOutputStream, session: SessionState) = with(session) {
        out.writeBoolean(enabled); out.writeBoolean(hasSession); out.writeLong(consumedMs)
        out.writeBoolean(exhausted); out.writeUTF(activity.name)
        out.writeLong(activeSince ?: -1); out.writeLong(breakSince ?: -1)
        out.writeBoolean(resetEarned); out.writeLong(generation); out.writeLong(observedAt)
        writeTemporary(out, temporary)
    }

    private fun readSession(input: DataInputStream): SessionState {
        fun anchor() = input.readLong().also { check(it >= -1) }.takeIf { it >= 0 }
        return SessionState(input.readBoolean(), input.readBoolean(), input.readLong(), input.readBoolean(),
            DeviceActivity.valueOf(input.readUTF()), anchor(), anchor(), input.readBoolean(), input.readLong(), input.readLong(), readTemporary(input))
    }

    private fun validate(session: SessionState) = with(session) {
        check(consumedMs in 0..Long.MAX_VALUE / 2 && generation >= 0 && observedAt >= 0)
        check(activeSince == null || (enabled && hasSession && activity == DeviceActivity.UNLOCKED && activeSince <= observedAt))
        check(breakSince == null || (enabled && activity == DeviceActivity.OFF && breakSince <= observedAt))
        check(!exhausted || hasSession)
        temporary?.let {
            check(enabled && hasSession && exhausted && activeSince == null)
            check(it.activeSince == null || (activity == DeviceActivity.UNLOCKED && it.activeSince <= observedAt))
        }
    }
}

internal interface CheckpointStorage {
    fun read(): String?
    fun commit(raw: String): Boolean
}

/** Keep the last confirmed value if commit mutates memory but fails to reach disk. */
internal class DurableMonitoringStore(private val storage: CheckpointStorage) : MonitoringStore {
    private var loaded = false
    private var confirmed: MonitoringCheckpoint = MonitoringCheckpoint()
    override fun load(): MonitoringCheckpoint {
        if (!loaded) {
            confirmed = storage.read()?.let(CheckpointCodec::decode) ?: MonitoringCheckpoint()
            loaded = true
        }
        return confirmed
    }
    override fun save(checkpoint: MonitoringCheckpoint) {
        load()
        check(storage.commit(CheckpointCodec.encode(checkpoint))) { "Could not commit monitoring checkpoint" }
        confirmed = checkpoint
    }
}
