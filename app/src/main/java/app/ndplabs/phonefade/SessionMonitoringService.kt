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
    private var lastNotification: NotificationPresentation? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_CHANGED) {
                // Notification.when is an epoch projection. Rebuild that projection
                // after a wall-clock change without changing any elapsed-time anchors.
                lastNotification = null
                val state = app.coordinator.state.value
                if (state.running) publishNotification(state)
                return
            }
            val signal = when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> ScreenSignal.SCREEN_OFF
                Intent.ACTION_SCREEN_ON -> ScreenSignal.SCREEN_ON
                Intent.ACTION_USER_PRESENT -> ScreenSignal.USER_PRESENT
                else -> return
            }
            val activity = app.activity()
            val observedAt = SystemClock.elapsedRealtime()
            val event = ScreenEventMapper.event(signal, activity)
            PhoneFadeLog.debug("receiver signal=$signal observed=$activity event=$event at=$observedAt")
            app.submit("receiver:$signal") { app.coordinator.event(event, observedAt) }
        }
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { app.submit("display observer") { app.coordinator.inspectDisplay() } }
    }

    override fun onCreate() {
        super.onCreate()
        PhoneFadeLog.debug("service onCreate")
        try {
            val notification = MonitoringNotification.create(this, NotificationPresentation("Starting monitoring…"))
            if (Build.VERSION.SDK_INT >= 34) startForeground(MonitoringNotification.ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(MonitoringNotification.ID, notification)
            promoted = true
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
            // USER_PRESENT is sent by SystemUI's separate UID, not system_server.
            // All four actions are protected broadcasts; ordinary apps cannot spoof them.
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else registerReceiver(receiver, filter)
            registered = true
            PhoneFadeLog.debug("receiver registered SCREEN_OFF SCREEN_ON USER_PRESENT TIME_SET exported=true")
            for (key in listOf(GrayscaleController.ENABLED, GrayscaleController.MODE)) {
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
        } catch (error: Exception) {
            PhoneFadeLog.debug("service startup failure type=${error.javaClass.simpleName}")
            app.submit("startupFailed") { app.coordinator.startupFailed(error.message ?: error.javaClass.simpleName) }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PhoneFadeLog.debug("service onStartCommand startId=$startId explicit=${intent?.action == START} promoted=$promoted")
        if (!promoted) return START_NOT_STICKY
        scope.launch {
            if (!app.startMonitoring(intent?.action == START, intent?.getLongExtra(REQUEST, -1))) {
                PhoneFadeLog.debug("service start rejected startId=$startId")
                if (stopSelfResult(startId)) stopForeground(STOP_FOREGROUND_REMOVE)
                return@launch
            }
            if (collector == null) collector = scope.launch {
                PhoneFadeLog.debug("notification collector started")
                app.coordinator.state.collect { state ->
                    PhoneFadeLog.state("notification update", state)
                    timer?.cancel()
                    if (!state.running) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@collect }
                    publishNotification(state)
                    val cp = state.checkpoint
                    val deadline = cp.session.deadline(cp.config)
                    if (deadline != null && cp.attention == null) timer = scope.launch {
                        // Minute checkpoints and one final-minute boundary refresh presentation;
                        // the absolute monotonic deadline controls correctness, never chronometer ticks.
                        val wait = NotificationCountdown.nextWakeDelay(deadline, SystemClock.elapsedRealtime())
                        PhoneFadeLog.debug("timer scheduled generation=${cp.session.generation} waitMs=$wait")
                        delay(wait)
                        val activity = app.activity()
                        val observedAt = SystemClock.elapsedRealtime()
                        app.submit("timer") {
                            if (app.coordinator.state.value.checkpoint.session.generation == cp.session.generation) {
                                if (activity != cp.session.activity) app.coordinator.event(SessionEvent.Observe(activity), observedAt)
                                else app.coordinator.event(SessionEvent.Deadline(cp.session.generation), observedAt)
                            }
                        }
                    }
                    else if (cp.session.activity == DeviceActivity.LOCKED && cp.attention == null) timer = scope.launch {
                        // USER_PRESENT is the primary unlock signal. Reconcile while
                        // locked too, including exhausted sessions with no deadline.
                        while (true) {
                            delay(1000L)
                            val activity = app.activity()
                            if (activity != DeviceActivity.LOCKED) {
                                val observedAt = SystemClock.elapsedRealtime()
                                app.submit("locked reconciliation") {
                                    if (app.coordinator.state.value.checkpoint.session.generation == cp.session.generation)
                                        app.coordinator.event(SessionEvent.Observe(activity), observedAt)
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        PhoneFadeLog.debug("service onDestroy registered=$registered")
        if (promoted) stopForeground(STOP_FOREGROUND_REMOVE)
        if (registered) unregisterReceiver(receiver)
        contentResolver.unregisterContentObserver(observer)
        scope.cancel()
        app.submit("serviceGone") { app.coordinator.serviceGone() }
        super.onDestroy()
    }
    private fun publishNotification(state: MonitoringState) {
        val presentation = NotificationCountdown.presentation(state, SystemClock.elapsedRealtime())
        if (presentation != lastNotification) {
            val notification = MonitoringNotification.create(this, presentation)
            // startForeground remains legal when notification permission is denied.
            startForeground(MonitoringNotification.ID, notification)
            lastNotification = presentation
            PhoneFadeLog.debug("notification published generation=${state.checkpoint.session.generation} " +
                "text=${presentation.text} countdownDeadline=${presentation.deadlineElapsed}")
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
