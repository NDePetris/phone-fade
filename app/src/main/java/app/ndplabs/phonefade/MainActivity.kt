package app.ndplabs.phonefade

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class GrayscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PhoneFadeApplication
    val state = app.coordinator.state
    fun refresh() = app.submit { app.coordinator.inspectDisplay() }
    fun enable() = app.submit { app.coordinator.manualEnable() }
    fun restore() = app.submit { app.coordinator.disable() }
    fun temporaryColor() = app.submit {
        app.coordinator.event(SessionEvent.Observe(app.activity()))
        app.coordinator.temporaryColor()
    }
    fun configure(config: MonitoringConfig) = app.submit { app.coordinator.configure(config) }
    fun start() {
        viewModelScope.launch {
            val request = app.requestStart() ?: return@launch
            try {
                app.startForegroundService(Intent(app, SessionMonitoringService::class.java).setAction(SessionMonitoringService.START)
                    .putExtra(SessionMonitoringService.REQUEST, request))
            } catch (error: Exception) {
                app.submit { app.coordinator.startupFailed(error.message ?: error.javaClass.simpleName) }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: GrayscaleViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            var notificationsVisible by remember { mutableStateOf(MonitoringNotification.visible(this)) }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsVisible = MonitoringNotification.visible(this)
                model.start()
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                model.refresh()
                notificationsVisible = MonitoringNotification.visible(this)
            }
            FadeTheme {
                FadeScreen(state, notificationsVisible, model::restore, model::enable,
                    model::temporaryColor, model::configure) {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else model.start()
                }
            }
        }
    }
}

internal fun recoveryCommands(original: ColorSettings): String {
    fun command(key: String, value: String?) = if (value == null)
        "adb shell settings --user current delete secure $key"
    else "adb shell settings --user current put secure $key '$value'"
    return listOf(
        command(GrayscaleController.ENABLED, "0"),
        command(GrayscaleController.MODE, original.mode),
        command(GrayscaleController.ENABLED, original.enabled),
    ).joinToString("\n")
}
