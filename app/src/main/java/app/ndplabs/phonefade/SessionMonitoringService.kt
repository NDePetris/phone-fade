package app.ndplabs.phonefade

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionMonitoringService : Service() {
    companion object {
        const val START = "app.ndplabs.phonefade.START"
        const val REQUEST = "request"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as PhoneFadeApplication
    private var timer: Job? = null
    private var collector: Job? = null
    private var registered = false
    private var promoted = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A stale boundary proves an intervening event was missed. Never join two
            // off intervals or fabricate unlocked use from that unknown gap.
            val activity = app.activity()
            val observedAt = SystemClock.elapsedRealtime()
            val missedBoundary = (intent.action == Intent.ACTION_SCREEN_ON && activity == DeviceActivity.OFF) ||
                (intent.action == Intent.ACTION_SCREEN_OFF && activity != DeviceActivity.OFF)
            val event = if (missedBoundary) SessionEvent.Recover(activity) else SessionEvent.Observe(activity)
            app.submit { app.coordinator.event(event, observedAt) }
        }
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { app.submit { app.coordinator.inspectDisplay() } }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val notification = MonitoringNotification.create(this, "Starting monitoring…")
            if (Build.VERSION.SDK_INT >= 34) startForeground(MonitoringNotification.ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(MonitoringNotification.ID, notification)
            promoted = true
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(receiver, filter)
            registered = true
            for (key in listOf(GrayscaleController.ENABLED, GrayscaleController.MODE)) {
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
        } catch (error: Exception) {
            app.submit { app.coordinator.startupFailed(error.message ?: error.javaClass.simpleName) }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!promoted) return START_NOT_STICKY
        scope.launch {
            if (!app.startMonitoring(intent?.action == START, intent?.getLongExtra(REQUEST, -1))) {
                if (stopSelfResult(startId)) stopForeground(STOP_FOREGROUND_REMOVE)
                return@launch
            }
            if (collector == null) collector = scope.launch {
                app.coordinator.state.collect { state ->
                    timer?.cancel()
                    if (!state.running) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@collect }
                    val notification = MonitoringNotification.create(this@SessionMonitoringService,
                        MonitoringNotification.text(state, SystemClock.elapsedRealtime()))
                    // startForeground remains legal when notification permission is denied.
                    startForeground(MonitoringNotification.ID, notification)
                    val cp = state.checkpoint
                    val deadline = cp.session.deadline(cp.config)
                    if (deadline != null && cp.attention == null) timer = scope.launch {
                        // Minute checkpoints also refresh presentation; the absolute deadline controls correctness.
                        val wait = minOf(60_000L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        delay(wait)
                        val activity = app.activity()
                        val observedAt = SystemClock.elapsedRealtime()
                        app.submit {
                            if (app.coordinator.state.value.checkpoint.session.generation == cp.session.generation) {
                                if (activity != cp.session.activity) app.coordinator.event(SessionEvent.Observe(activity), observedAt)
                                else app.coordinator.event(SessionEvent.Deadline(cp.session.generation), observedAt)
                            }
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(receiver)
        contentResolver.unregisterContentObserver(observer)
        scope.cancel()
        app.submit { app.coordinator.serviceGone() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
