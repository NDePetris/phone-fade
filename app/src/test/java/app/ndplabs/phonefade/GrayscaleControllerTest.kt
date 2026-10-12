package app.ndplabs.phonefade

import org.junit.Assert.*
import org.junit.Test

class GrayscaleControllerTest {
    private class FakeSettings(original: ColorSettings = ColorSettings("0", "12")) : DisplaySettings {
        val values = mutableMapOf(GrayscaleController.ENABLED to original.enabled, GrayscaleController.MODE to original.mode)
        var permission = true
        var permissionError = false
        var readError = false
        var failWriteAt = -1
        var throwWriteAt = -1
        var ignoreWriteAt = -1
        var mutateThenFailAt = -1
        var revokeAt = -1
        val writes = mutableListOf<Pair<String, String?>>()
        override fun hasWritePermission(): Boolean {
            if (permissionError) error("permission provider unavailable")
            return permission
        }
        override fun read(key: String): String? {
            if (readError) error("read unavailable")
            return values[key]
        }
        override fun write(key: String, value: String?): Boolean {
            writes += key to value
            val count = writes.size
            if (count == throwWriteAt) throw SecurityException("write denied")
            if (count == failWriteAt) return false
            if (count != ignoreWriteAt) values[key] = value
            if (count == revokeAt) permission = false
            return count != mutateThenFailAt
        }
        fun pair() = ColorSettings(values[GrayscaleController.ENABLED], values[GrayscaleController.MODE])
    }

    private class FakeRecovery : RecoveryStore {
        var original: ColorSettings? = null
        var saveError = false
        var loadError = false
        var clearError = false
        override fun load(): ColorSettings? {
            if (loadError) error("corrupt journal")
            return original
        }
        override fun save(original: ColorSettings) {
            if (saveError) error("disk full")
            this.original = original
        }
        override fun clear() {
            if (clearError) error("disk full")
            original = null
        }
    }

    private val settings = FakeSettings()
    private val recovery = FakeRecovery()
    private val controller = GrayscaleController(settings, recovery)

    @Test fun missingPermissionPreventsAllWrites() {
        settings.permission = false
        val result = controller.enable()
        assertFalse(result.permissionGranted)
        assertTrue(settings.writes.isEmpty())
        assertNull(recovery.original)
    }

    @Test fun permissionCheckExceptionIsReported() {
        settings.permissionError = true
        assertTrue(controller.enable().message.contains("Permission check failed"))
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun refusesEveryExistingEnabledConfigurationIncludingGrayscale() {
        for (enabled in listOf("1", "2", "unexpected", "")) {
            settings.values[GrayscaleController.ENABLED] = enabled
            assertTrue(controller.enable().message.contains("will not take control"))
            assertTrue(settings.writes.isEmpty())
            assertNull(recovery.original)
        }
    }

    @Test fun refusesUnknownModeWithoutWrites() {
        settings.values[GrayscaleController.MODE] = "unexpected"
        assertTrue(controller.enable().message.contains("Unrecognized"))
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun enablesModeBeforeEnabledAndSavesOriginal() {
        val result = controller.enable()
        assertEquals(ColorSettings("1", "0"), result.current)
        assertEquals(ColorSettings("0", "12"), recovery.original)
        assertEquals(listOf(GrayscaleController.MODE to "0", GrayscaleController.ENABLED to "1"), settings.writes)
    }

    @Test fun snapshotIsPersistedBeforeFirstWrite() {
        val delegate = object : DisplaySettings by settings {
            override fun write(key: String, value: String?): Boolean {
                assertEquals(ColorSettings("0", "12"), recovery.original)
                return settings.write(key, value)
            }
        }
        GrayscaleController(delegate, recovery).enable()
        assertEquals(ColorSettings("1", "0"), settings.pair())
    }

    @Test fun restoreUsesExactSnapshotAndClearsOnlyAfterVerification() {
        controller.enable()
        controller.restore()
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNull(recovery.original)
        assertEquals(listOf(GrayscaleController.ENABLED to "0", GrayscaleController.MODE to "12", GrayscaleController.ENABLED to "0"), settings.writes.takeLast(3))
    }

    @Test fun restoresAbsentSettingsAsAbsent() {
        val absent = FakeSettings(ColorSettings(null, null))
        val instance = GrayscaleController(absent, recovery)
        instance.enable()
        assertEquals(ColorSettings(null, null), recovery.original)
        instance.restore()
        assertEquals(ColorSettings(null, null), absent.pair())
        assertNull(recovery.original)
    }

    @Test fun repeatedEnableDoesNotReplaceSnapshotOrWriteAgain() {
        controller.enable()
        assertTrue(controller.enable().message.contains("Restore Color"))
        assertEquals(2, settings.writes.size)
        assertEquals(ColorSettings("0", "12"), recovery.original)
    }

    @Test fun repeatedRestoreAndUnownedRestoreNeverWrite() {
        controller.restore()
        assertTrue(settings.writes.isEmpty())
        controller.enable()
        controller.restore()
        val count = settings.writes.size
        controller.restore()
        assertEquals(count, settings.writes.size)
    }

    @Test fun repeatedCyclesPreserveOriginalEachTime() {
        repeat(5) {
            controller.enable()
            assertEquals(ColorSettings("1", "0"), settings.pair())
            controller.restore()
            assertEquals(ColorSettings("0", "12"), settings.pair())
        }
    }

    @Test fun snapshotSaveFailurePreventsSettingsWrites() {
        recovery.saveError = true
        assertTrue(controller.enable().message.contains("disk full"))
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun initialReadFailurePreventsSettingsWrites() {
        settings.readError = true
        assertTrue(controller.enable().message.contains("read unavailable"))
        assertTrue(settings.writes.isEmpty())
        assertNull(recovery.original)
    }

    @Test fun corruptJournalBlocksActivationAndRestoration() {
        recovery.loadError = true
        assertTrue(controller.enable().message.contains("Recovery record unavailable"))
        assertTrue(controller.restore().message.contains("Restore failed"))
        assertTrue(settings.writes.isEmpty())
    }

    @Test fun rejectedFirstWriteRollsBack() {
        settings.failWriteAt = 1
        assertTrue(controller.enable().message.contains("Enable failed"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNull(recovery.original)
    }

    @Test fun rejectedSecondWriteRollsBack() {
        settings.failWriteAt = 2
        assertTrue(controller.enable().message.contains("Original settings restored"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNull(recovery.original)
    }

    @Test fun exceptionDuringWriteRollsBack() {
        settings.throwWriteAt = 2
        assertTrue(controller.enable().message.contains("write denied"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
    }

    @Test fun writeThatMutatesThenReturnsFalseIsRecovered() {
        settings.mutateThenFailAt = 2
        assertTrue(controller.enable().message.contains("Enable failed"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNull(recovery.original)
    }

    @Test fun successfulReturnWithoutMutationIsDetected() {
        settings.ignoreWriteAt = 1
        assertTrue(controller.enable().message.contains("Read-back mismatch"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
    }

    @Test fun revokedPermissionDuringEnableRetainsSnapshotForLaterRecovery() {
        settings.revokeAt = 1
        assertTrue(controller.enable().message.contains("Recovery required"))
        assertEquals(ColorSettings("0", "12"), recovery.original)
        assertFalse(controller.restore().permissionGranted)
        settings.permission = true
        controller.restore()
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNull(recovery.original)
    }

    @Test fun revokedPermissionBeforeRestoreDoesNotWriteOrLoseSnapshot() {
        controller.enable()
        settings.permission = false
        val count = settings.writes.size
        assertTrue(controller.restore().message.contains("Permission missing"))
        assertEquals(count, settings.writes.size)
        assertNotNull(recovery.original)
    }

    @Test fun interruptedActivationAndRestorationRecoverInNewController() {
        for (current in listOf(ColorSettings("0", "12"), ColorSettings("0", "0"), ColorSettings("1", "0"))) {
            recovery.original = ColorSettings("0", "12")
            settings.values[GrayscaleController.ENABLED] = current.enabled
            settings.values[GrayscaleController.MODE] = current.mode
            GrayscaleController(settings, recovery).restore()
            assertEquals(ColorSettings("0", "12"), settings.pair())
            assertNull(recovery.original)
        }
    }

    @Test fun restorationFailuresKeepJournalAndCanBeRetried() {
        for (step in 1..3) {
            val local = FakeSettings()
            val journal = FakeRecovery()
            val instance = GrayscaleController(local, journal)
            instance.enable()
            local.failWriteAt = 2 + step
            assertTrue(instance.restore().message.contains("Restore failed"))
            assertNotNull(journal.original)
            instance.restore()
            assertEquals(ColorSettings("0", "12"), local.pair())
            assertNull(journal.original)
        }
    }

    @Test fun journalClearFailureRetainsOriginalAndRetryDoesNotWrite() {
        controller.enable()
        recovery.clearError = true
        assertTrue(controller.restore().message.contains("Restore failed"))
        assertEquals(ColorSettings("0", "12"), settings.pair())
        assertNotNull(recovery.original)
        val count = settings.writes.size
        recovery.clearError = false
        controller.restore()
        assertEquals(count, settings.writes.size)
        assertNull(recovery.original)
    }

    @Test fun externalColorCorrectionConflictPreventsRestoreWrites() {
        controller.enable()
        settings.values[GrayscaleController.MODE] = "11"
        val count = settings.writes.size
        assertTrue(controller.restore().message.contains("outside Phone Fade"))
        assertEquals(count, settings.writes.size)
        assertNotNull(recovery.original)
    }

    @Test fun settingsChangeBeforeActivationIsDetected() {
        val delegate = object : RecoveryStore by recovery {
            override fun save(original: ColorSettings) {
                recovery.save(original)
                settings.values[GrayscaleController.ENABLED] = "1"
                settings.values[GrayscaleController.MODE] = "11"
            }
        }
        assertTrue(GrayscaleController(settings, delegate).enable().message.contains("changed before activation"))
        assertTrue(settings.writes.isEmpty())
        assertNotNull(recovery.original)
    }

    @Test fun statusRechecksPermissionAndNeverWrites() {
        assertTrue(controller.status().permissionGranted)
        settings.permission = false
        assertFalse(controller.status().permissionGranted)
        assertTrue(settings.writes.isEmpty())
    }
}
