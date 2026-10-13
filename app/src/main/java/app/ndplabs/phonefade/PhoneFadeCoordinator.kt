package app.ndplabs.phonefade

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MonitoringState(
    val checkpoint: MonitoringCheckpoint = MonitoringCheckpoint(),
    val display: ControllerStatus = ControllerStatus(),
    val running: Boolean = false,
    val ready: Boolean = false,
    val starting: Boolean = false,
)

/** All commands are serialized, including manual actions and boot recovery. No Android dependencies. */
class PhoneFadeCoordinator(
    private val controller: GrayscaleController,
    private val store: MonitoringStore,
    private val clock: () -> Long,
) {
    private var checkpoint = MonitoringCheckpoint()
    private var initialized = false
    private var storageFailed = false
    private var running = false
    private var startRequest = 0L
    private var starting = false
    private val mutableState = MutableStateFlow(MonitoringState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun initialize(bootCount: Int?, userStopped: Boolean = false) {
        if (initialized) return
        initialized = true
        try {
            checkpoint = store.load()
            val reboot = checkpoint.bootCount == null || bootCount == null || checkpoint.bootCount != bootCount
            if (reboot || userStopped) {
                checkpoint = checkpoint.copy(session = SessionState(), bootCount = bootCount,
                    pending = DisplayEffect.RESTORE, interrupted = false,
                    attention = if (userStopped) "Monitoring was stopped by the user. Enable it explicitly to restart." else null)
                persist()
                perform(DisplayEffect.RESTORE)
            } else {
                checkpoint = checkpoint.copy(session = checkpoint.session.copy(activeSince = null, breakSince = null,
                    activity = DeviceActivity.LOCKED, observedAt = clock(), generation = checkpoint.session.generation + 1),
                    interrupted = checkpoint.session.enabled || checkpoint.interrupted)
                when (checkpoint.pending) {
                    DisplayEffect.RESTORE -> perform(DisplayEffect.RESTORE)
                    DisplayEffect.ENABLE -> {
                        val display = controller.status()
                        if (display.ownsGrayscale) checkpoint = checkpoint.copy(pending = null)
                        else if (display.original != null || !display.readable) {
                            checkpoint = checkpoint.copy(session = SessionState(), pending = DisplayEffect.RESTORE)
                            perform(DisplayEffect.RESTORE)
                            checkpoint = checkpoint.copy(attention = "Activation was interrupted. Review display settings and enable again.")
                        }
                        // No snapshot: activation had not started; retry only after service startup.
                    }
                    null -> Unit
                }
                persist()
            }
        } catch (error: Exception) { failStorage(error) }
        publish()
    }

    @Synchronized
    fun requestStart(): Long? {
        if (!initialized || storageFailed || running) return null
        if (!starting) { startRequest++; starting = true; publish() }
        return startRequest
    }

    @Synchronized
    fun start(activity: DeviceActivity, explicit: Boolean, request: Long? = null): Boolean {
        check(initialized)
        if (running) return true // repeated onStartCommand must not reset an existing session
        if (explicit && request != null && (!starting || request != startRequest)) return false
        starting = false
        if (storageFailed) return false
        if (!explicit && (!checkpoint.session.enabled || checkpoint.attention != null)) return false
        val resume = checkpoint.session.enabled && checkpoint.attention == null
        val display = controller.status()
        if (!explicit || resume) {
            val compatible = display.readable && display.permissionGranted &&
                if (checkpoint.session.exhausted) display.ownsGrayscale || display.canEnable
                else display.canEnable
            if (!compatible) {
                attention("Monitoring was interrupted and display ownership could not be reconciled. Disable and restore, then enable again.")
                return false
            }
        }
        if (explicit && !resume) {
            // New activations start clean; interrupted sessions take the resume path above.
            if (display.original != null || !display.readable) {
                val restored = controller.restore()
                if (!restored.operationSucceeded) { attention(restored.message); return false }
            }
            if (!controller.status().canEnable) {
                attention("Cannot start: secure-settings permission or compatible color settings required.")
                return false
            }
            checkpoint = checkpoint.copy(session = SessionState(generation = checkpoint.session.generation + 1),
                attention = null, interrupted = false, pending = null)
        }
        running = true
        transition(if (explicit && !resume) SessionEvent.Enable(activity) else SessionEvent.Recover(activity))
        if (resume && checkpoint.pending == DisplayEffect.ENABLE && checkpoint.attention == null) perform(DisplayEffect.ENABLE)
        publish()
        return running
    }

    @Synchronized
    fun event(event: SessionEvent, observedAt: Long = clock()) {
        if (running && checkpoint.attention == null && !storageFailed) {
            if (event is SessionEvent.Recover) checkpoint = checkpoint.copy(interrupted = true)
            transition(event, observedAt)
        }
        publish()
    }

    @Synchronized
    fun configure(config: MonitoringConfig) {
        if (storageFailed) return
        // Account time using the old active anchor before applying the new allowance.
        checkpoint = checkpoint.copy(config = config)
        if (running && checkpoint.attention == null) transition(SessionEvent.ConfigurationChanged)
        else persistSafely()
        publish()
    }

    @Synchronized
    fun disable() {
        starting = false
        startRequest++
        running = false
        checkpoint = checkpoint.copy(session = SessionState(generation = checkpoint.session.generation + 1),
            pending = DisplayEffect.RESTORE, attention = null, interrupted = false)
        // Restoration remains available even when checkpoint storage is broken.
        persistSafely()
        perform(DisplayEffect.RESTORE)
        publish()
    }

    @Synchronized
    fun startupFailed(message: String) {
        disable()
        attention("Monitoring could not start: $message")
    }

    @Synchronized
    fun serviceGone() {
        if (running) {
            // Account only through this reliable observation; destruction is not guaranteed.
            val now = clock()
            checkpoint = checkpoint.copy(session = checkpoint.session.copy(consumedMs = checkpoint.session.consumedAt(now),
                activeSince = null, breakSince = null), interrupted = true)
            running = false
            persistSafely()
            publish()
        }
    }

    @Synchronized
    fun inspectDisplay() {
        val display = controller.status()
        if (running && checkpoint.attention == null) {
            val expected = display.readable && display.permissionGranted &&
                if (checkpoint.session.exhausted) display.ownsGrayscale ||
                    (checkpoint.session.activity != DeviceActivity.UNLOCKED && display.canEnable)
                else display.canEnable
            if (!expected) attention("Display settings or permission changed. Disable and restore, then review setup.")
        }
        publish()
    }

    @Synchronized
    fun manualEnable() {
        if (!running && !checkpoint.session.enabled) {
            val result = controller.enable()
            publish(result)
        }
    }

    @Synchronized
    fun bootRecovery(bootCount: Int?) {
        starting = false
        startRequest++
        running = false
        checkpoint = checkpoint.copy(session = SessionState(), bootCount = bootCount,
            pending = DisplayEffect.RESTORE, interrupted = false, attention = null)
        persistSafely()
        perform(DisplayEffect.RESTORE)
        publish()
    }

    private fun transition(event: SessionEvent, observedAt: Long = clock()) {
        val previous = checkpoint.session
        val transition = SessionEngine.reduce(checkpoint.session, event, observedAt, checkpoint.config)
        if (transition.state == checkpoint.session && transition.effect == null) return
        checkpoint = checkpoint.copy(session = transition.state, pending = transition.effect ?: checkpoint.pending)
        if (!persistSafely()) return
        transition.effect?.let { effect ->
            // A reset is only successful after verified restoration. On failure keep
            // prior usage, but retain the new screen boundary and stop all counting.
            val failedReset = if (effect == DisplayEffect.RESTORE && transition.state.enabled)
                transition.state.copy(hasSession = previous.hasSession, consumedMs = previous.consumedAt(observedAt),
                    exhausted = previous.exhausted, activeSince = null, resetEarned = false)
            else null
            perform(effect, failedReset)
        }
    }

    private fun perform(effect: DisplayEffect, failedReset: SessionState? = null) {
        val display = controller.status()
        val result = when (effect) {
            DisplayEffect.ENABLE -> if (display.ownsGrayscale) display.copy(operationSucceeded = true) else controller.enable()
            DisplayEffect.RESTORE -> controller.restore()
        }
        if (result.operationSucceeded) {
            checkpoint = checkpoint.copy(pending = null)
            persistSafely()
        } else {
            if (failedReset != null) checkpoint = checkpoint.copy(session = failedReset)
            attention(result.message)
        }
    }

    private fun attention(message: String) {
        checkpoint = checkpoint.copy(attention = message)
        persistSafely()
        publish()
    }

    private fun persist() = store.save(checkpoint)
    private fun persistSafely(): Boolean = try { persist(); true } catch (error: Exception) { failStorage(error); false }
    private fun failStorage(error: Exception) {
        starting = false
        storageFailed = true
        running = false
        checkpoint = checkpoint.copy(session = checkpoint.session.copy(enabled = false, activeSince = null, breakSince = null),
            attention = "Monitoring storage failed: ${error.message}. Restore original settings; restart the app after fixing storage.")
    }
    private fun publish(display: ControllerStatus = controller.status()) {
        mutableState.value = MonitoringState(checkpoint, display, running, initialized, starting)
    }
}
