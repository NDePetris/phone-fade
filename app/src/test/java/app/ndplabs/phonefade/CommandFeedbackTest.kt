package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class CommandFeedbackTest {
    private class Fixture {
        var current = ColorSettings(null, "12")
        var original: ColorSettings? = null
        var permission = true
        var failWrites = false
        var failClear = false
        var commits = 0
        var failCommitAt = Int.MAX_VALUE
        var saved = MonitoringCheckpoint(bootCount = 1)
        val controller = GrayscaleController(object : DisplaySettings {
            override fun hasWritePermission() = permission
            override fun read(key: String) = if (key == GrayscaleController.ENABLED) current.enabled else current.mode
            override fun write(key: String, value: String?): Boolean {
                if (failWrites) return false
                current = if (key == GrayscaleController.ENABLED) current.copy(enabled = value) else current.copy(mode = value)
                return true
            }
        }, object : RecoveryStore {
            override fun load() = original
            override fun save(original: ColorSettings) { this@Fixture.original = original }
            override fun clear() { check(!failClear); original = null }
        })
        val coordinator = PhoneFadeCoordinator(controller, object : MonitoringStore {
            override fun load() = saved
            override fun save(checkpoint: MonitoringCheckpoint) {
                commits++
                check(commits != failCommitAt) { "Injected storage failure" }
                saved = checkpoint
            }
        }) { 0L }.also { it.initialize(1) }
    }
    @Test fun saveAcknowledgedOnlyAfterDurableCommit() {
        val f = Fixture()
        assertEquals(CommandFeedback.SAVED, f.coordinator.configure(MonitoringConfig(2, 3, 4)))
        assertEquals(MonitoringConfig(2, 3, 4), f.saved.config)
        assertEquals(f.saved.config, f.coordinator.state.value.savedConfig)
    }
    @Test fun failedSaveKeepsLastDurableConfigAndError() {
        val f = Fixture(); val saved = f.saved.config
        f.failCommitAt = f.commits + 1
        assertEquals(CommandFeedback.FAILED, f.coordinator.configure(MonitoringConfig(2, 3, 4)))
        assertEquals(saved, f.saved.config)
        assertEquals(saved, f.coordinator.state.value.savedConfig)
        assertTrue(f.coordinator.state.value.checkpoint.attention!!.contains("storage failed"))
        assertEquals(CommandFeedback.FAILED, f.coordinator.configure(MonitoringConfig()))
    }
    @Test fun actualRestorePreservesAbsentKeyAndAcknowledgesCompletion() {
        val f = Fixture(); f.coordinator.manualEnable()
        assertNotNull(f.original)
        assertEquals(CommandFeedback.RESTORED, f.coordinator.disable())
        assertEquals(ColorSettings(null, "12"), f.current); assertNull(f.original)
        assertNull(f.saved.pending); assertNull(f.saved.attention)
        assertEquals(CommandFeedback.NOTHING_TO_RESTORE, f.coordinator.disable())
    }
    @Test fun noSnapshotIsExplicitNoOpEvenWithoutPermission() {
        val f = Fixture(); f.permission = false
        assertEquals(CommandFeedback.NOTHING_TO_RESTORE, f.coordinator.disable())
        assertEquals(ColorSettings(null, "12"), f.current)
    }
    @Test fun permissionFailureRetainsRecoveryWithoutSuccess() {
        val f = Fixture(); f.coordinator.manualEnable(); f.permission = false
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertNotNull(f.original); assertNotNull(f.coordinator.state.value.checkpoint.attention)
    }
    @Test fun writeFailureRetainsRecoveryWithoutSuccess() {
        val f = Fixture(); f.coordinator.manualEnable(); f.failWrites = true
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertNotNull(f.original); assertEquals(DisplayEffect.RESTORE, f.coordinator.state.value.checkpoint.pending)
    }
    @Test fun journalClearFailureIsNotSuccessfulRestoration() {
        val f = Fixture(); f.coordinator.manualEnable(); f.failClear = true
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertNotNull(f.original); assertNotNull(f.coordinator.state.value.checkpoint.attention)
    }
    @Test fun completionCommitFailureDoesNotAcknowledgePhysicalWrite() {
        val f = Fixture(); f.coordinator.manualEnable(); f.failCommitAt = f.commits + 2
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertEquals(ColorSettings(null, "12"), f.current); assertNull(f.original)
        assertEquals(DisplayEffect.RESTORE, f.coordinator.state.value.checkpoint.pending)
        assertNotNull(f.coordinator.state.value.checkpoint.attention)
    }
    @Test fun initialDisableCommitFailureStillAllowsRecoveryButNeverSuccessFeedback() {
        val f = Fixture(); f.coordinator.manualEnable(); f.failCommitAt = f.commits + 1
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertEquals(ColorSettings(null, "12"), f.current)
        assertNotNull(f.coordinator.state.value.checkpoint.attention)
    }
    @Test fun externalConflictIsNotOverwrittenOrAcknowledged() {
        val f = Fixture(); f.coordinator.manualEnable(); f.current = ColorSettings("1", "11")
        assertEquals(CommandFeedback.FAILED, f.coordinator.disable())
        assertEquals(ColorSettings("1", "11"), f.current); assertNotNull(f.original)
    }
}
