package app.ndplabs.phonefade

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun FadeScreen(state: MonitoringState, notificationsVisible: Boolean, restore: () -> Unit,
    manualEnable: () -> Unit, temporaryColor: () -> Unit, configure: (MonitoringConfig) -> Unit,
    start: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = SystemClock.elapsedRealtime()
            while (isActive && MonitoringPresentation.counting(state)) {
                delay(CountdownFormatter.nextRefresh(MonitoringPresentation.remaining(state, now)))
                now = SystemClock.elapsedRealtime()
                if (MonitoringPresentation.remaining(state, now) == 0L) break
            }
        }
    }
    val cp = state.checkpoint
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_fade_mark), null, Modifier.size(32.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
            }
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Only the stable state label announces; the changing countdown is outside its live region.
                Text(MonitoringPresentation.status(state), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(MonitoringPresentation.text(state, now), style = MaterialTheme.typography.headlineSmall)
                if (cp.session.temporary != null) Text("Counts only while unlocked. Your ordinary allowance remains exhausted.")
                if (state.display.ownsGrayscale || cp.session.temporary != null) Text(MonitoringPresentation.breakMessage(cp.config))
                else Text("Unlocked use counts toward your color allowance. A continuous screen-off break earns a reset.")
                cp.attention?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (!notificationsVisible) Text("Notifications are hidden or blocked. Monitoring can continue, but its notice may only appear in Android’s Active apps list.")
                val enabled = state.running || state.starting || cp.session.enabled
                val stopOrRecover = state.running || state.starting || cp.attention != null
                Button(onClick = if (stopOrRecover) restore else start,
                    enabled = state.ready && (stopOrRecover || state.display.permissionGranted), modifier = Modifier.fillMaxWidth()) {
                    Text(if (stopOrRecover) {
                        if (enabled) "Disable and restore" else "Restore original settings"
                    } else if (cp.session.enabled) "Resume monitoring" else "Enable monitoring")
                }
                if (cp.session.enabled && !state.running && !state.starting && cp.attention == null) {
                    OutlinedButton(onClick = restore, enabled = state.ready, modifier = Modifier.fillMaxWidth()) { Text("Disable and restore") }
                }
                if (MonitoringPresentation.canUseTemporaryColor(state)) {
                    OutlinedButton(onClick = temporaryColor, modifier = Modifier.fillMaxWidth()) { Text("Use temporary color") }
                    Text("${CountdownFormatter.minutes(cp.config.temporaryMinutes)} of unlocked use. This does not reset your allowance.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (cp.attention != null || (!enabled && state.display.original != null)) {
                    OutlinedButton(onClick = restore, enabled = state.ready, modifier = Modifier.fillMaxWidth()) { Text("Restore original settings") }
                }
            }
            HorizontalDivider()
            TimingSettings(cp.config, state.ready && cp.attention == null, configure)
            HorizontalDivider()
            if (!state.display.permissionGranted) {
                Text("Permission setup", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("On a trusted computer, install Android Platform Tools. Enable USB debugging, connect your phone, and approve its debugging prompt. Run:")
                SelectionContainer { Text(GrayscaleController.GRANT_COMMAND, fontFamily = FontFamily.Monospace) }
                Text("Return here to refresh. This development grant has no normal Android permission dialog.")
            }
            TextButton(onClick = { diagnostics = !diagnostics }) {
                Text(if (diagnostics) "Hide recovery and diagnostics" else "Recovery and diagnostics")
            }
            if (diagnostics || cp.attention != null) {
                Text(state.display.message)
                Text(if (state.display.permissionGranted) "Secure-settings permission granted" else "Secure-settings permission missing")
                state.display.current?.let { Text("Current settings: enabled=${it.enabled ?: "absent"}, mode=${it.mode ?: "absent"}") }
                state.display.original?.let { original ->
                    Text("Saved recovery settings", style = MaterialTheme.typography.titleMedium)
                    Text("Keep this snapshot until restoration succeeds. Inspect external changes before using these commands for the same Android user:")
                    SelectionContainer { Text(recoveryCommands(original), fontFamily = FontFamily.Monospace) }
                }
                OutlinedButton(onClick = restore, enabled = state.ready, modifier = Modifier.fillMaxWidth()) { Text("Restore original settings") }
                OutlinedButton(onClick = manualEnable,
                    enabled = state.ready && !state.running && !state.starting && !cp.session.enabled && state.display.canEnable,
                    modifier = Modifier.fillMaxWidth()) { Text("Enable manual grayscale") }
            }
            Text("Restore before uninstalling or clearing app data. Android can interrupt monitoring; read-back verifies settings, not the physical display effect.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimingSettings(config: MonitoringConfig, enabled: Boolean, save: (MonitoringConfig) -> Unit) {
    var allowance by rememberSaveable(config.allowanceMinutes) { mutableStateOf(config.allowanceMinutes.toString()) }
    var reset by rememberSaveable(config.resetMinutes) { mutableStateOf(config.resetMinutes.toString()) }
    var temporary by rememberSaveable(config.temporaryMinutes) { mutableStateOf(config.temporaryMinutes.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Timing settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        MinuteField("Color allowance", allowance, enabled) { allowance = it }
        MinuteField("Reset interval", reset, enabled) { reset = it }
        MinuteField("Temporary color duration", temporary, enabled) { temporary = it }
        Text("Settings preserve consumed usage. Temporary duration changes apply next time. An exhausted allowance stays exhausted until a qualifying break.",
            style = MaterialTheme.typography.bodyMedium)
        val values = listOf(allowance, reset, temporary).map { it.toIntOrNull() }
        val valid = values.all { it in 1..120 }
        OutlinedButton(onClick = { save(MonitoringConfig(values[0]!!, values[1]!!, values[2]!!)) },
            enabled = enabled && valid, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
    }
}

@Composable
private fun MinuteField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    val invalid = value.toIntOrNull() !in 1..120
    OutlinedTextField(value, change, label = { Text("$label (minutes)") }, enabled = enabled,
        isError = invalid, supportingText = { Text(if (invalid) "Enter a whole number from 1 to 120" else "1–120 minutes") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
