package app.ndplabs.phonefade

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive

@Composable
fun FadeScreen(state: MonitoringState, notificationsVisible: Boolean, feedback: Flow<CommandFeedback> = emptyFlow(),
    restore: () -> Unit, manualEnable: () -> Unit, temporaryColor: () -> Unit,
    configure: (MonitoringConfig) -> Unit, start: () -> Unit) {
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
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(feedback, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            feedback.collect { result -> result.message?.let { snackbars.showSnackbar(it) } }
        }
    }
    val cp = state.checkpoint
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val needsRecovery = cp.attention != null || (!state.running && state.display.original != null && !state.display.ownsGrayscale)
    LaunchedEffect(needsRecovery) { if (needsRecovery) diagnostics = true }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_offramp_family), null, Modifier.size(40.dp, 41.5625.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() })
                }
                StatusHero(state, now, restore, temporaryColor, start)
                if (state.ready) TimingSettings(state.savedConfig, cp.attention == null, configure)
                else Text("Loading saved timing settings…", style = MaterialTheme.typography.bodyMedium)
                if (!state.display.permissionGranted) PermissionSetup()
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Disclosure("Recovery and diagnostics", diagnostics, R.drawable.ic_road_shield) { diagnostics = !diagnostics }
                        if (diagnostics) {
                            Text("Display and permission status", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            Text(state.display.message)
                            Text(if (state.display.permissionGranted) "Secure-settings permission granted" else "Secure-settings permission missing")
                            state.display.current?.let { Text("Current settings: enabled=${it.enabled ?: "absent"}, mode=${it.mode ?: "absent"}",
                                style = MaterialTheme.typography.bodySmall) }
                            Text("Read-back verifies Android settings, not the physical display effect. Check your display after restoration.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Saved recovery information", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            state.display.original?.let { original ->
                                Text("Original settings: enabled=${original.enabled ?: "absent"}, mode=${original.mode ?: "absent"}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Keep this snapshot until restoration succeeds. Inspect external changes before using these commands for the same Android user:")
                                SelectionContainer { Text(recoveryCommands(original), fontFamily = FontFamily.Monospace) }
                            } ?: Text("No saved original settings.", style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Recovery and manual controls", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            OutlinedButton(onClick = restore, enabled = state.ready, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("Restore original settings")
                            }
                            OutlinedButton(onClick = manualEnable,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                enabled = state.ready && !state.running && !state.starting && !cp.session.enabled && state.display.canEnable,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Enable manual grayscale") }
                            if (state.running) Text("A qualifying screen-off break restores settings at wake. Counting starts after unlock. Short breaks do not add up.")
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                if (!notificationsVisible) Text("Notifications are hidden or blocked. Monitoring can continue, but its notice may only appear in Android’s Active apps list.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Restore before uninstalling or clearing app data. Android can interrupt monitoring. Settings checks don't verify the display effect.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
internal fun StatusHero(state: MonitoringState, now: Long, restore: () -> Unit, temporaryColor: () -> Unit, start: () -> Unit) {
    val hero = QuietRoadPresentation.hero(state, now)
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box {
            QuietRoadLandscape()
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .08f)) {
                        Icon(painterResource(if (state.checkpoint.attention != null) R.drawable.ic_road_attention else R.drawable.ic_road_leaf),
                            null, Modifier.padding(10.dp).size(22.dp))
                    }
                    // Only stable status/cause announces. Countdown ticks are never live regions.
                    Text(hero.status, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Column(Modifier.clearAndSetSemantics {
                    heading()
                    val spokenTime = hero.countdown?.let(QuietRoadPresentation::spokenCountdown)
                    contentDescription = listOfNotNull(hero.title, spokenTime).joinToString(", ")
                }) {
                    Text(hero.title, style = if (hero.countdown == null) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    hero.countdown?.let { countdown ->
                        val parts = countdown.split(" ", limit = 2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(parts[0], style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            if (parts.size == 2) Text(parts[1], style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 8.dp))
                        }
                    }
                }
                Text(hero.guidance, style = MaterialTheme.typography.bodyMedium)
                state.checkpoint.attention?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp)) {
                        Text(it, Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                hero.history?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
                fun click(action: HeroAction) = when (action) {
                    HeroAction.START, HeroAction.RESUME -> start
                    HeroAction.TEMPORARY -> temporaryColor
                    HeroAction.DISABLE, HeroAction.RESTORE -> restore
                }
                fun enabled(action: HeroAction) = state.ready && when (action) {
                    HeroAction.START, HeroAction.RESUME -> state.display.permissionGranted
                    HeroAction.TEMPORARY -> MonitoringPresentation.canUseTemporaryColor(state)
                    else -> true
                }
                hero.primary?.let { action ->
                    Button(onClick = click(action), enabled = enabled(action), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(action.label)
                    }
                    if (action == HeroAction.TEMPORARY) Text("${CountdownFormatter.minutes(state.savedConfig.temporaryMinutes)} of unlocked use. Allowance stays exhausted.",
                        style = MaterialTheme.typography.bodySmall)
                }
                hero.secondary?.let { action ->
                    OutlinedButton(onClick = click(action), enabled = enabled(action), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimingSettings(config: MonitoringConfig, enabled: Boolean, save: (MonitoringConfig) -> Unit) {
    // Drafts stay composed when the disclosure closes; saved config never previews edits.
    var allowance by rememberSaveable { mutableStateOf(config.allowanceMinutes.toString()) }
    var reset by rememberSaveable { mutableStateOf(config.resetMinutes.toString()) }
    var temporary by rememberSaveable { mutableStateOf(config.temporaryMinutes.toString()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val draft = TimingDraft(allowance, reset, temporary)
    val dirty = draft.changed(config)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_road_clock), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Timing settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            TimingValue("Color allowance", config.allowanceMinutes)
            TimingValue("Reset interval", config.resetMinutes)
            TimingValue("Temporary color", config.temporaryMinutes)
            if (dirty) Text("Unsaved changes", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Disclosure("Edit timing settings", expanded, R.drawable.ic_road_edit) { expanded = !expanded }
            if (!enabled) Text("Timing settings are unavailable while attention is required or the app is initializing.", style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinuteField("Color allowance", allowance, config.allowanceMinutes, enabled) { allowance = it }
                    MinuteField("Reset interval", reset, config.resetMinutes, enabled) { reset = it }
                    MinuteField("Temporary color duration", temporary, config.temporaryMinutes, enabled) { temporary = it }
                }
                Text("Temporary duration changes apply next time. Saving does not clear used time or reset an exhausted allowance.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { draft.config()?.let(save) }, enabled = draft.canSave(config, enabled),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save settings") }
            }
        }
    }
}

@Composable
private fun TimingValue(label: String, minutes: Int) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(CountdownFormatter.minutes(minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MinuteField(label: String, value: String, active: Int, enabled: Boolean, change: (String) -> Unit) {
    val invalid = value.toIntOrNull() !in 1..120
    val supporting = QuietRoadPresentation.fieldSupportingText(value, active)
    val density = LocalDensity.current
    var extraLabelOutset by remember(label, density) { mutableStateOf(0.dp) }
    // The floating label is centered on the field's top boundary. Reserve its
    // extra measured outset when text scaling or wrapping exceeds one native line.
    OutlinedTextField(value, change, label = {
        Text("$label (minutes)", onTextLayout = { layout ->
            extraLabelOutset = ((with(density) { layout.size.height.toDp() } - 16.dp) / 2).coerceAtLeast(0.dp)
        })
    }, enabled = enabled, isError = invalid,
        supportingText = supporting?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = extraLabelOutset))
}

@Composable
private fun Disclosure(label: String, expanded: Boolean, icon: Int, toggle: () -> Unit) {
    TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        stateDescription = if (expanded) "Expanded" else "Collapsed"
    }) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Text(label, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge)
        Icon(painterResource(if (expanded) R.drawable.ic_road_up else R.drawable.ic_road_down), null, Modifier.size(20.dp))
    }
}

@Composable
private fun PermissionSetup() {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permission setup required", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("Secure-settings permission is missing. On a trusted computer, install Android Platform Tools. Enable USB debugging, connect your phone, and approve its debugging prompt. Run:",
                style = MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(GrayscaleController.GRANT_COMMAND, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            Text("Return here to refresh. This development grant has no normal Android permission dialog.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
