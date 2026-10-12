package app.ndplabs.phonefade

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class PhoneFadeApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    lateinit var coordinator: PhoneFadeCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        val preferences = getSharedPreferences("phonefade_monitoring", MODE_PRIVATE)
        val store = DurableMonitoringStore(object : CheckpointStorage {
            override fun read() = preferences.getString("checkpoint", null)
            @SuppressLint("UseKtx") // commit Boolean is part of the durability protocol
            override fun commit(raw: String) = preferences.edit().putString("checkpoint", raw).commit()
        })
        coordinator = PhoneFadeCoordinator(GrayscaleController(AndroidDisplaySettings(this),
            PreferencesRecoveryStore(this)), store, SystemClock::elapsedRealtime)
        scope.launch { for (command in commands) command() }
        submit { coordinator.initialize(bootCount(), userStopped()) }
    }

    fun submit(command: () -> Unit) { check(commands.trySend(command).isSuccess) }

    suspend fun requestStart(): Long? {
        val result = CompletableDeferred<Long?>()
        submit { result.complete(coordinator.requestStart()) }
        return result.await()
    }

    suspend fun startMonitoring(explicit: Boolean, request: Long?): Boolean {
        val result = CompletableDeferred<Boolean>()
        submit { result.complete(coordinator.start(activity(), explicit, request)) }
        return result.await()
    }

    fun activity(): DeviceActivity = when {
        !getSystemService(PowerManager::class.java).isInteractive -> DeviceActivity.OFF
        getSystemService(KeyguardManager::class.java).isKeyguardLocked -> DeviceActivity.LOCKED
        else -> DeviceActivity.UNLOCKED
    }

    fun bootCount(): Int? = try { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }
    catch (_: Exception) { null }

    private fun userStopped(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        try {
            val latest = getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
            latest?.reason == ApplicationExitInfo.REASON_USER_REQUESTED
        } catch (_: Exception) { false }
    } else false
}
