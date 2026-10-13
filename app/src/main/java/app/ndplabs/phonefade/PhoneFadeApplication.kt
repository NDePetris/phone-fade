package app.ndplabs.phonefade

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
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
import java.util.concurrent.atomic.AtomicLong

class PhoneFadeApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val commandId = AtomicLong()
    lateinit var coordinator: PhoneFadeCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        PhoneFadeLog.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        PhoneFadeLog.debug("application onCreate")
        val preferences = getSharedPreferences("phonefade_monitoring", MODE_PRIVATE)
        val store = DurableMonitoringStore(object : CheckpointStorage {
            override fun read() = preferences.getString("checkpoint", null)
            @SuppressLint("UseKtx") // commit Boolean is part of the durability protocol
            override fun commit(raw: String) = preferences.edit().putString("checkpoint", raw).commit()
        })
        coordinator = PhoneFadeCoordinator(GrayscaleController(AndroidDisplaySettings(this),
            PreferencesRecoveryStore(this)), store, SystemClock::elapsedRealtime)
        scope.launch {
            PhoneFadeLog.debug("command processor started")
            for (command in commands) command()
        }
        submit("initialize") { coordinator.initialize(bootCount(), userStopped()) }
    }

    fun submit(label: String = "command", command: () -> Unit) {
        val id = commandId.incrementAndGet()
        PhoneFadeLog.debug("command queued id=$id source=$label")
        check(commands.trySend {
            PhoneFadeLog.state("command begin id=$id source=$label", coordinator.state.value)
            try {
                command()
            } catch (error: Exception) {
                PhoneFadeLog.debug("command failed id=$id source=$label type=${error.javaClass.simpleName}")
                throw error
            }
            PhoneFadeLog.state("command end id=$id source=$label", coordinator.state.value)
        }.isSuccess)
    }

    suspend fun requestStart(): Long? {
        val result = CompletableDeferred<Long?>()
        submit("requestStart") { result.complete(coordinator.requestStart()) }
        return result.await()
    }

    suspend fun startMonitoring(explicit: Boolean, request: Long?): Boolean {
        val result = CompletableDeferred<Boolean>()
        submit("startMonitoring") { result.complete(coordinator.start(activity(), explicit, request)) }
        return result.await()
    }

    fun activity(): DeviceActivity {
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val keyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        PhoneFadeLog.debug("device snapshot interactive=$interactive keyguardLocked=$keyguardLocked")
        return when {
            !interactive -> DeviceActivity.OFF
            keyguardLocked -> DeviceActivity.LOCKED
            else -> DeviceActivity.UNLOCKED
        }
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
