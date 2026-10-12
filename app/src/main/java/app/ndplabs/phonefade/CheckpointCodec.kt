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
            out.writeInt(1)
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
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(raw: String): MonitoringCheckpoint = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw))).use { input ->
        check(input.readInt() == 1) { "Unknown monitoring checkpoint version" }
        val config = MonitoringConfig(input.readInt(), input.readInt())
        val boot = input.readInt().also { check(it >= -1) }.takeIf { it >= 0 }
        fun anchor(): Long? = input.readLong().also { check(it >= -1) }.takeIf { it >= 0 }
        val session = SessionState(input.readBoolean(), input.readBoolean(), input.readLong(), input.readBoolean(),
            DeviceActivity.valueOf(input.readUTF()), anchor(), anchor(), input.readBoolean(), input.readLong(), input.readLong())
        check(session.consumedMs in 0..Long.MAX_VALUE / 2 && session.generation >= 0 && session.observedAt >= 0)
        check(session.activeSince == null || (session.enabled && session.hasSession && session.activity == DeviceActivity.UNLOCKED && session.activeSince <= session.observedAt))
        check(session.breakSince == null || (session.enabled && session.activity == DeviceActivity.OFF && session.breakSince <= session.observedAt))
        check(!session.exhausted || session.hasSession)
        val pending = input.readUTF().takeIf { it.isNotEmpty() }?.let { DisplayEffect.valueOf(it) }
        val interrupted = input.readBoolean()
        val attention = if (input.readBoolean()) input.readUTF() else null
        check(input.available() == 0) { "Trailing checkpoint data" }
        MonitoringCheckpoint(config, session, boot, pending, attention, interrupted)
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
