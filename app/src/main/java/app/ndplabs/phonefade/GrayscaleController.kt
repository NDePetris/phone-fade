package app.ndplabs.phonefade

/** Only these two secure settings are ever read or written. Null means an absent row. */
interface DisplaySettings {
    fun hasWritePermission(): Boolean
    fun read(key: String): String?
    fun write(key: String, value: String?): Boolean
}

data class ColorSettings(val enabled: String?, val mode: String?)

/** Must persist synchronously before returning; errors must throw. */
interface RecoveryStore {
    fun load(): ColorSettings?
    fun save(original: ColorSettings)
    fun clear()
}

data class ControllerStatus(
    val permissionGranted: Boolean = false,
    val current: ColorSettings? = null,
    val original: ColorSettings? = null,
    val message: String = "Checking display settings…",
)

class GrayscaleController(
    private val settings: DisplaySettings,
    private val recovery: RecoveryStore,
) {
    companion object {
        // Hidden AOSP settings: deliberately use strings, not hidden SDK APIs.
        const val ENABLED = "accessibility_display_daltonizer_enabled"
        const val MODE = "accessibility_display_daltonizer"
        const val GRANT_COMMAND =
            "adb shell pm grant app.ndplabs.phonefade android.permission.WRITE_SECURE_SETTINGS"
    }

    @Synchronized
    fun status(): ControllerStatus = inspect("Ready. This spike only changes color manually.")

    @Synchronized
    fun enable(): ControllerStatus {
        try {
            if (!settings.hasWritePermission()) return inspect("Permission missing. Complete ADB setup first.")
            if (recovery.load() != null) return inspect("Recovery record exists. Restore Color before enabling again.")
            val original = readPair()
            if (original.enabled != null && original.enabled != "0") {
                return inspect("Color correction is already active or unrecognized. Phone Fade will not take control.")
            }
            check(original.mode == null || original.mode.matches(Regex("-?[0-9]+"))) {
                "Unrecognized color-correction mode; no settings changed"
            }
            recovery.save(original)
            // Recheck after saving to reduce (but cannot eliminate) cross-process races.
            check(readPair() == original) { "Display settings changed before activation" }
            writeVerified(MODE, "0")
            writeVerified(ENABLED, "1")
            check(readPair() == ColorSettings("1", "0")) { "Activation verification failed" }
            return inspect("Grayscale settings applied. Confirm the visual effect on your device.")
        } catch (error: Exception) {
            // A write may have changed the provider even when it threw or returned false.
            val rollback = try { restoreSaved(); "Original settings restored." }
            catch (_: Exception) { "Recovery required. Use Restore Color or the saved ADB commands." }
            return inspect("Enable failed: ${error.description()}. $rollback")
        }
    }

    @Synchronized
    fun restore(): ControllerStatus = try {
        if (recovery.load() == null) inspect("No saved configuration. Nothing changed.")
        else {
            check(settings.hasWritePermission()) { "Permission missing; grant it again to restore" }
            restoreSaved()
            inspect("Original color-correction settings restored.")
        }
    } catch (error: Exception) {
        inspect("Restore failed: ${error.description()}. Recovery record retained; see saved ADB commands.")
    }

    private fun restoreSaved() {
        val original = recovery.load() ?: return
        check(original.enabled == null || original.enabled == "0") { "Invalid saved enabled value" }
        check(original.mode == null || original.mode.matches(Regex("-?[0-9]+"))) { "Invalid saved mode" }
        val current = readPair()
        // Every interrupted combination of our writes is recoverable. Refuse unfamiliar
        // values so that a later accessibility configuration is not silently overwritten.
        check(current.enabled in listOf(original.enabled, "0", "1") &&
            current.mode in listOf(original.mode, "0") &&
            !(current.enabled == "1" && current.mode != "0")) {
            "Display settings changed outside Phone Fade; inspect them before manual recovery"
        }
        if (current == original) {
            recovery.clear()
            return
        }
        check(settings.hasWritePermission()) { "Write permission unavailable" }
        // Disable our transform before restoring the original mode (which may be absent).
        writeVerified(ENABLED, "0")
        writeVerified(MODE, original.mode)
        writeVerified(ENABLED, original.enabled)
        check(readPair() == original) { "Restoration verification failed" }
        recovery.clear()
    }

    private fun writeVerified(key: String, value: String?) {
        check(settings.hasWritePermission()) { "Write permission unavailable" }
        check(settings.write(key, value)) { "Write rejected for $key" }
        check(settings.read(key) == value) { "Read-back mismatch for $key" }
    }

    private fun readPair() = ColorSettings(settings.read(ENABLED), settings.read(MODE))

    private fun inspect(message: String): ControllerStatus {
        var permission = false
        var current: ColorSettings? = null
        var original: ColorSettings? = null
        val errors = mutableListOf<String>()
        try { permission = settings.hasWritePermission() }
        catch (error: Exception) { errors += "Permission check failed: ${error.description()}" }
        try { original = recovery.load() }
        catch (error: Exception) { errors += "Recovery record unavailable: ${error.description()}. Consult your preflight ADB snapshot." }
        try { current = readPair() }
        catch (error: Exception) { errors += "Settings read failed: ${error.description()}" }
        return ControllerStatus(permission, current, original, (listOf(message) + errors).joinToString("\n"))
    }

    private fun Exception.description() = message ?: javaClass.simpleName
}
