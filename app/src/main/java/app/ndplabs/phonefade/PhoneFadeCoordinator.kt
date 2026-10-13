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
    private var effectObservationWindow: LongRange? = null
    private var lastEffectObservationAt = -1L
    private val mutableState = MutableStateFlow(MonitoringState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun initialize(bootCount: Int?, userStopped: Boolean = false) {
        if (initialized) return
        initialized = true
        try {
            checkpoint = store.load()
            // Also support injected legacy checkpoints; the disk codec performs this migration.
            if (checkpoint.pending != null && checkpoint.pendingPurpose == null) checkpoint = checkpoint.copy(
                pendingPurpose = if (checkpoint.pending == DisplayEffect.ENABLE) OperationPurpose.ACTIVATE
                    else if (checkpoint.session.enabled) OperationPurpose.RESET else OperationPurpose.DISABLE,
                pendingTarget = checkpoint.session)
            val reboot = checkpoint.bootCount == null || bootCount == null || checkpoint.bootCount != bootCount
            if (reboot || userStopped) {
                checkpoint = checkpoint.copy(session = SessionState(), bootCount = bootCount,
                    pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.DISABLE,
                    pendingTarget = SessionState(), expectedBaseline = null, interrupted = false,
                    attention = if (userStopped) "Monitoring was stopped by the user. Enable it explicitly to restart." else null)
                persist()
                perform(DisplayEffect.RESTORE)
            } else {
                checkpoint = checkpoint.copy(session = withoutAnchors(checkpoint.session),
                    pendingTarget = checkpoint.pendingTarget?.let(::withoutAnchors),
                    interrupted = checkpoint.session.enabled || checkpoint.interrupted)
                when (checkpoint.pending) {
                    DisplayEffect.RESTORE -> perform(DisplayEffect.RESTORE)
                    DisplayEffect.ENABLE -> {
                        val display = controller.status()
                        if (display.ownsGrayscale && (checkpoint.expectedBaseline == null || display.original == checkpoint.expectedBaseline)) {
                            completeOperation()
                        }
                        else if (display.original != null || !display.readable) {
                            // A partial activation is restored, never silently replaced or retried.
                            val result = controller.restore(checkpoint.expectedBaseline)
                            if (result.operationSucceeded) checkpoint = checkpoint.copy(pending = null, pendingPurpose = null, pendingTarget = null)
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
                if (checkpoint.session.temporary != null) display.canEnable && display.current == checkpoint.expectedBaseline
                else
                if (checkpoint.session.exhausted) display.ownsGrayscale || display.canEnable
                else display.canEnable
            if (!compatible) {
                attention("Monitoring was interrupted and display ownership could not be reconciled. Disable and restore, then enable again.")
                return false
            }
        }
        if (explicit && !resume) {
            if (checkpoint.pending != null || checkpoint.session.temporary != null) {
                attention("Recovery is pending. Disable and restore before starting a new session.")
                return false
            }
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
                attention = null, interrupted = false, pending = null, pendingPurpose = null, pendingTarget = null, expectedBaseline = null)
        }
        running = true
        transition(if (explicit && !resume) SessionEvent.Enable(activity) else SessionEvent.Recover(activity))
        if (resume && checkpoint.pending == DisplayEffect.ENABLE && checkpoint.attention == null && activity == DeviceActivity.UNLOCKED) perform(DisplayEffect.ENABLE)
        publish()
        return running
    }

    @Synchronized
    fun event(event: SessionEvent, observedAt: Long = clock()) {
        if (running && checkpoint.attention == null && !storageFailed) {
            // A screen signal can be captured while synchronous display writes block
            // this queue. Completion advances the usage anchor. Reconcile those signals
            // conservatively instead of discarding a real lock/off boundary as obsolete.
            val activity = when (event) {
                is SessionEvent.Observe -> event.activity
                is SessionEvent.Recover -> event.activity
                else -> null
            }
            val duringEffect = activity != null && effectObservationWindow?.contains(observedAt) == true
            if (duringEffect) {
                if (observedAt >= lastEffectObservationAt) {
                    lastEffectObservationAt = observedAt
                    checkpoint = checkpoint.copy(interrupted = true)
                    transition(SessionEvent.Recover(activity!!))
                }
            } else {
                if (effectObservationWindow?.let { observedAt > it.last } == true) effectObservationWindow = null
                if (event is SessionEvent.Recover) checkpoint = checkpoint.copy(interrupted = true)
                transition(event, observedAt)
            }
        }
        publish()
    }

    @Synchronized
    fun temporaryColor() {
        val display = controller.status()
        if (!running || storageFailed || checkpoint.attention != null || checkpoint.pending != null ||
            !checkpoint.session.exhausted || checkpoint.session.temporary != null ||
            checkpoint.session.activity != DeviceActivity.UNLOCKED || !display.permissionGranted || !display.ownsGrayscale) return
        checkpoint = checkpoint.copy(expectedBaseline = display.original)
        transition(SessionEvent.RequestTemporaryColor)
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
            pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.DISABLE,
            pendingTarget = SessionState(generation = checkpoint.session.generation + 1),
            expectedBaseline = null, attention = null, interrupted = false)
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
                temporary = checkpoint.session.temporary?.let { it.copy(consumedMs = it.consumedAt(now), activeSince = null) },
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
                if (checkpoint.session.temporary != null) display.canEnable && display.current == checkpoint.expectedBaseline
                else
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
            pending = DisplayEffect.RESTORE, pendingPurpose = OperationPurpose.DISABLE,
            pendingTarget = SessionState(), expectedBaseline = null, interrupted = false, attention = null)
        persistSafely()
        perform(DisplayEffect.RESTORE)
        publish()
    }

    private fun transition(event: SessionEvent, observedAt: Long = clock()) {
        val previous = checkpoint.session
        val transition = SessionEngine.reduce(checkpoint.session, event, observedAt, checkpoint.config)
        if (transition.state == checkpoint.session && transition.effect == null) return
        // Keep confirmed usage/exhaustion until the display operation completes. Persist
        // the target separately so process death cannot turn a restoration into a reset.
        if (transition.effect != null) {
            val retained = transition.state.copy(hasSession = previous.hasSession,
                consumedMs = previous.consumedAt(observedAt), exhausted = previous.exhausted,
                temporary = previous.temporary?.let { it.copy(consumedMs = it.consumedAt(observedAt), activeSince = null) },
                activeSince = null, resetEarned = false)
            checkpoint = checkpoint.copy(session = retained, pending = transition.effect,
                pendingPurpose = transition.purpose, pendingTarget = transition.state)
        } else checkpoint = checkpoint.copy(session = transition.state)
        if (!persistSafely()) return
        transition.effect?.let { perform(it) }
    }

    private fun perform(effect: DisplayEffect) {
        val display = controller.status()
        val result = when (effect) {
            DisplayEffect.ENABLE -> if (display.ownsGrayscale &&
                (checkpoint.expectedBaseline == null || display.original == checkpoint.expectedBaseline)) display.copy(operationSucceeded = true)
                else controller.enable(checkpoint.expectedBaseline)
            DisplayEffect.RESTORE -> controller.restore(checkpoint.expectedBaseline)
        }
        if (result.operationSucceeded) {
            completeOperation()
        } else {
            attention(result.message)
        }
    }

    private fun completeOperation() {
        val before = checkpoint
        val now = clock()
        var target = checkpoint.pendingTarget ?: checkpoint.session
        effectObservationWindow = if (running && target.enabled && now > target.observedAt) target.observedAt until now else null
        lastEffectObservationAt = -1L
        if (checkpoint.pendingPurpose == OperationPurpose.TEMPORARY_COLOR) target = target.copy(
            temporary = target.temporary?.copy(activeSince = if (running && target.activity == DeviceActivity.UNLOCKED) now else null))
        if (!running) target = withoutAnchors(target)
        else target = target.copy(observedAt = now, activeSince = target.activeSince?.let { now })
        checkpoint = checkpoint.copy(session = target, pending = null, pendingPurpose = null, pendingTarget = null,
            expectedBaseline = if (target.temporary != null) checkpoint.expectedBaseline else null)
        try { persist() } catch (error: Exception) {
            // Do not publish a successful override when the completion journal failed.
            checkpoint = before
            failStorage(error)
        }
    }

    private fun withoutAnchors(session: SessionState) = session.copy(activeSince = null, breakSince = null,
        temporary = session.temporary?.copy(activeSince = null), activity = DeviceActivity.LOCKED,
        observedAt = clock(), generation = session.generation + 1)

    private fun attention(message: String) {
        val now = clock()
        checkpoint = checkpoint.copy(attention = message, session = checkpoint.session.copy(
            consumedMs = checkpoint.session.consumedAt(now), activeSince = null, breakSince = null,
            temporary = checkpoint.session.temporary?.let { it.copy(consumedMs = it.consumedAt(now), activeSince = null) }))
        persistSafely()
        publish()
    }

    private fun persist() = store.save(checkpoint)
    private fun persistSafely(): Boolean = try { persist(); true } catch (error: Exception) { failStorage(error); false }
    private fun failStorage(error: Exception) {
        starting = false
        storageFailed = true
        running = false
        checkpoint = checkpoint.copy(session = checkpoint.session.copy(activeSince = null, breakSince = null,
            temporary = checkpoint.session.temporary?.copy(activeSince = null)),
            attention = "Monitoring storage failed: ${error.message}. Restore original settings; restart the app after fixing storage.")
    }
    private fun publish(display: ControllerStatus = controller.status()) {
        mutableState.value = MonitoringState(checkpoint, display, running, initialized, starting)
    }
}
