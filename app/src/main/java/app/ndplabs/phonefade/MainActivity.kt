package app.ndplabs.phonefade

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Phone Fade", style = MaterialTheme.typography.headlineMedium)
                        Text("Automatic grayscale", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.starting) "Starting monitoring…" else MonitoringNotification.text(state, SystemClock.elapsedRealtime()))
                        state.checkpoint.attention?.let { Text(it) }
                        if (!notificationsVisible) Text("Notifications are hidden or blocked. Monitoring can continue, but its notification may only appear in Android’s Active apps list.")
                        val config = state.checkpoint.config
                        var allowance by remember(config) { mutableStateOf(config.allowanceMinutes.toString()) }
                        var reset by remember(config) { mutableStateOf(config.resetMinutes.toString()) }
                        OutlinedTextField(value = allowance, onValueChange = { allowance = it }, label = { Text("Color allowance (minutes)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = reset, onValueChange = { reset = it }, label = { Text("Reset interval (minutes)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val allowanceValue = allowance.toIntOrNull()
                        val resetValue = reset.toIntOrNull()
                        Text("Use 1–120 minutes. Settings preserve consumed time; an exhausted session stays exhausted until a qualifying break.")
                        OutlinedButton(onClick = { model.configure(MonitoringConfig(allowanceValue!!, resetValue!!)) },
                            enabled = state.ready && allowanceValue in 1..120 && resetValue in 1..120,
                            modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else model.start()
                        }, enabled = state.ready && !state.running && !state.starting && state.display.permissionGranted,
                            modifier = Modifier.fillMaxWidth()) { Text("Enable automatic monitoring") }
                        Text("A continuous screen-off break earns fresh color time. Display timeout also starts a break; waking to the lock screen ends it.")
                        Text(state.display.message)
                        Text(if (state.display.permissionGranted) "Secure-settings permission granted" else "Secure-settings permission missing")
                        val current = state.display.current
                        if (current != null) Text("Current settings: enabled=${current.enabled ?: "absent"}, mode=${current.mode ?: "absent"}")
                        Button(
                            onClick = model::enable,
                            enabled = state.ready && !state.running && !state.starting && !state.checkpoint.session.enabled && state.display.permissionGranted &&
                                current != null && (current.enabled == null || current.enabled == "0") &&
                                state.display.original == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Enable Grayscale") }
                        OutlinedButton(
                            onClick = model::restore,
                            enabled = state.ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.running || state.starting || state.checkpoint.session.enabled) "Disable and restore original settings" else "Restore original settings") }
                        if (!state.display.permissionGranted) {
                            Text("Setup", style = MaterialTheme.typography.titleLarge)
                            Text("On a trusted computer, install Android Platform Tools. Enable Developer options and USB debugging, connect your phone, and approve its debugging prompt. Run:")
                            SelectionContainer { Text(GrayscaleController.GRANT_COMMAND, fontFamily = FontFamily.Monospace) }
                            Text("Return to this app to refresh permission status. Android does not provide a normal permission dialog for this grant.")
                        }
                        state.display.original?.let { original ->
                            Text("Saved recovery settings", style = MaterialTheme.typography.titleLarge)
                            Text("Restore Color uses this snapshot, including absent settings. Keep it until recovery succeeds. If the app cannot restore, inspect the settings first, then run these commands for the same Android user:")
                            SelectionContainer {
                                Text(recoveryCommands(original), fontFamily = FontFamily.Monospace)
                            }
                        }
                        Text("Restore before uninstalling or clearing app data. Display behavior depends on the Android build; verify the effect on your phone.")
                    }
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
