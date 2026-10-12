package app.ndplabs.phonefade

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreenState(val status: ControllerStatus = ControllerStatus(), val busy: Boolean = false)

class GrayscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = GrayscaleController(
        AndroidDisplaySettings(application), PreferencesRecoveryStore(application),
    )
    private val mutableState = MutableStateFlow(ScreenState())
    val state = mutableState.asStateFlow()

    fun refresh() = run(controller::status)
    fun enable() = run(controller::enable)
    fun restore() = run(controller::restore)

    private fun run(operation: () -> ControllerStatus) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            mutableState.value = ScreenState(result)
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
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Phone Fade", style = MaterialTheme.typography.headlineMedium)
                        Text("Manual grayscale capability spike", style = MaterialTheme.typography.titleMedium)
                        Text(state.status.message)
                        Text(if (state.status.permissionGranted) "Secure-settings permission granted" else "Secure-settings permission missing")
                        val current = state.status.current
                        if (current != null) Text("Current settings: enabled=${current.enabled ?: "absent"}, mode=${current.mode ?: "absent"}")
                        Button(
                            onClick = model::enable,
                            enabled = !state.busy && state.status.permissionGranted &&
                                current != null && (current.enabled == null || current.enabled == "0") &&
                                state.status.original == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Enable Grayscale") }
                        OutlinedButton(
                            onClick = model::restore,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Restore Color") }
                        if (!state.status.permissionGranted) {
                            Text("Setup", style = MaterialTheme.typography.titleLarge)
                            Text("On a trusted computer, install Android Platform Tools. Enable Developer options and USB debugging, connect your phone, and approve its debugging prompt. Run:")
                            SelectionContainer { Text(GrayscaleController.GRANT_COMMAND, fontFamily = FontFamily.Monospace) }
                            Text("Return to this app to refresh permission status. Android does not provide a normal permission dialog for this grant.")
                        }
                        state.status.original?.let { original ->
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
