package app.ndplabs.phonefade

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONObject

class AndroidDisplaySettings(private val context: Context) : DisplaySettings {
    override fun hasWritePermission() = context.checkSelfPermission(
        Manifest.permission.WRITE_SECURE_SETTINGS,
    ) == PackageManager.PERMISSION_GRANTED

    override fun read(key: String): String? = Settings.Secure.getString(context.contentResolver, key)

    override fun write(key: String, value: String?): Boolean =
        Settings.Secure.putString(context.contentResolver, key, value)
}

class PreferencesRecoveryStore(context: Context) : RecoveryStore {
    private val preferences = context.getSharedPreferences("grayscale_recovery", Context.MODE_PRIVATE)
    // SharedPreferences changes its in-memory map even when commit() returns false.
    // Keep the last record visible if clearing it could not be persisted.
    private var retainedAfterClearFailure: String? = null

    override fun load(): ColorSettings? {
        val raw = retainedAfterClearFailure ?: preferences.getString("original", null) ?: return null
        val json = JSONObject(raw)
        check(json.getInt("version") == 1) { "Unknown recovery format" }
        fun value(key: String): String? {
            check(json.has(key)) { "Incomplete recovery record" }
            val value = json.get(key)
            check(value == JSONObject.NULL || value is String) { "Invalid recovery value" }
            return if (value == JSONObject.NULL) null else value as String
        }
        val original = ColorSettings(value("enabled"), value("mode"))
        check(original.enabled == null || original.enabled == "0") { "Invalid saved enabled value" }
        check(original.mode == null || original.mode.matches(Regex("-?[0-9]+"))) { "Invalid saved mode" }
        return original
    }

    // KTX edit discards the commit Boolean, which this recovery protocol requires.
    @SuppressLint("UseKtx")
    override fun save(original: ColorSettings) {
        val raw = JSONObject().put("version", 1)
            .put("enabled", original.enabled ?: JSONObject.NULL)
            .put("mode", original.mode ?: JSONObject.NULL).toString()
        check(preferences.edit().putString("original", raw).commit()) { "Could not save recovery record" }
    }

    @SuppressLint("UseKtx")
    override fun clear() {
        val raw = retainedAfterClearFailure ?: preferences.getString("original", null)
        if (!preferences.edit().remove("original").commit()) {
            retainedAfterClearFailure = raw
            error("Could not clear recovery record")
        }
        retainedAfterClearFailure = null
    }
}
