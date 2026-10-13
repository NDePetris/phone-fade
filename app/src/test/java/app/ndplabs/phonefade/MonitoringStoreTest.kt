package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class MonitoringStoreTest {
    @Test fun roundTripAllFields() {
        val value = MonitoringCheckpoint(MonitoringConfig(2, 3), SessionState(true, true, 20, true,
            DeviceActivity.OFF, null, 100, true, 4, 100), 9, DisplayEffect.RESTORE, "Attention", true)
        assertEquals(value, CheckpointCodec.decode(CheckpointCodec.encode(value)))
    }
    @Test fun failedCommitRetainsConfirmedValueEvenIfMemoryChanged() {
        var memory: String? = null
        var succeed = true
        val store = DurableMonitoringStore(object : CheckpointStorage {
            override fun read() = memory
            override fun commit(raw: String): Boolean { memory = raw; return succeed }
        })
        val original = MonitoringCheckpoint(bootCount = 1)
        store.save(original)
        succeed = false
        try { store.save(original.copy(bootCount = 2)); fail() } catch (_: IllegalStateException) {}
        assertEquals(original, store.load())
    }
    @Test fun malformedRecordNeverSilentlyResets() {
        for (raw in listOf("not base64", "AAAAAg==", CheckpointCodec.encode(MonitoringCheckpoint()).dropLast(4))) {
            try { CheckpointCodec.decode(raw); fail("Accepted malformed record") } catch (_: Exception) {}
        }
    }
}
