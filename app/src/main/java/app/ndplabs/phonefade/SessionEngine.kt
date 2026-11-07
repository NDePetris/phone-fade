package app.ndplabs.phonefade

data class MonitoringConfig(val allowanceMinutes: Int = 10, val resetMinutes: Int = 5, val temporaryMinutes: Int = 5) {
    init { require(allowanceMinutes in 1..120 && resetMinutes in 1..120 && temporaryMinutes in 1..120) }
    val allowanceMs get() = allowanceMinutes * 60_000L
    val resetMs get() = resetMinutes * 60_000L
    val temporaryMs get() = temporaryMinutes * 60_000L
}

enum class DeviceActivity { OFF, LOCKED, UNLOCKED }
enum class DisplayEffect { ENABLE, RESTORE }
enum class OperationPurpose { ACTIVATE, RESET, DISABLE, TEMPORARY_COLOR, REACTIVATE }

/** Once durably armed, the anchor never moves, including across process/service recovery.
 * consumedMs is a fixed offset for migrated v2 usage, not an unlocked-use counter.
 * A null anchor is reserved for an entry operation that has not completed. */
data class TemporaryColorState(val durationMs: Long, val consumedMs: Long = 0, val activeSince: Long? = null) {
    val deadlineElapsed get() = activeSince?.let { it + (durationMs - consumedMs).coerceAtLeast(0) }
    fun consumedAt(now: Long) = consumedMs + (activeSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remaining(now: Long) = (durationMs - consumedAt(now)).coerceAtLeast(0)
}

data class SessionState(
    val enabled: Boolean = false,
    val hasSession: Boolean = false,
    val consumedMs: Long = 0,
    val exhausted: Boolean = false,
    val activity: DeviceActivity = DeviceActivity.LOCKED,
    val activeSince: Long? = null,
    val breakSince: Long? = null,
    val resetEarned: Boolean = false,
    val generation: Long = 0,
    val observedAt: Long = 0,
    val temporary: TemporaryColorState? = null,
) {
    fun consumedAt(now: Long): Long = consumedMs + (activeSince?.takeIf { temporary == null }?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remaining(config: MonitoringConfig, now: Long): Long =
        if (exhausted) 0 else (config.allowanceMs - consumedAt(now)).coerceAtLeast(0)
    fun deadline(config: MonitoringConfig): Long? {
        if (!enabled) return null
        temporary?.let {
            // An observed expired interval waits for unlock/reset, without a zero-delay timer loop.
            return if (activity != DeviceActivity.UNLOCKED && it.remaining(observedAt) == 0L) null else it.deadlineElapsed
        }
        return if (!exhausted && activeSince != null) activeSince + (config.allowanceMs - consumedMs).coerceAtLeast(0) else null
    }
}

sealed interface SessionEvent {
    data class Enable(val activity: DeviceActivity) : SessionEvent
    data object Disable : SessionEvent
    data class Observe(val activity: DeviceActivity) : SessionEvent
    data class Deadline(val generation: Long) : SessionEvent
    data object ConfigurationChanged : SessionEvent
    data class Recover(val activity: DeviceActivity) : SessionEvent
    data class RequestTemporaryColor(val durationMinutes: Int) : SessionEvent
}

data class SessionTransition(val state: SessionState, val effect: DisplayEffect? = null,
    val purpose: OperationPurpose? = null)

/** Android-free reducer. Timestamps are supplied by one monotonic clock. */
object SessionEngine {
    fun reduce(old: SessionState, event: SessionEvent, now: Long, config: MonitoringConfig): SessionTransition {
        require(now >= 0)
        if (event is SessionEvent.Deadline && event.generation != old.generation) return SessionTransition(old)
        if (event == SessionEvent.Disable) return SessionTransition(
            SessionState(generation = old.generation + 1, observedAt = now), DisplayEffect.RESTORE, OperationPurpose.DISABLE,
        )
        if (event is SessionEvent.Enable) {
            if (old.enabled) return SessionTransition(old)
            return reduce(SessionState(enabled = true, generation = old.generation + 1), SessionEvent.Observe(event.activity), now, config)
        }
        if (!old.enabled) return SessionTransition(old)
        if (now < old.observedAt) return SessionTransition(old) // obsolete observation
        if (event is SessionEvent.Recover) {
            // Never infer unlocked use or a continuous break during process downtime.
            val recovered = old.copy(activeSince = null, breakSince = null, activity = DeviceActivity.LOCKED,
                observedAt = now, generation = old.generation + 1)
            return reduce(recovered, SessionEvent.Observe(event.activity), now, config)
        }
        var state = old.copy(consumedMs = old.consumedAt(now), activeSince = null,
            observedAt = now, generation = old.generation + 1)
        var effect: DisplayEffect? = null
        var purpose: OperationPurpose? = null
        if (event is SessionEvent.RequestTemporaryColor) {
            if (event.durationMinutes !in 1..120 || !old.exhausted || old.temporary != null ||
                old.activity != DeviceActivity.UNLOCKED) return SessionTransition(old)
            return SessionTransition(state.copy(temporary = TemporaryColorState(event.durationMinutes * 60_000L)),
                DisplayEffect.RESTORE, OperationPurpose.TEMPORARY_COLOR)
        }
        if (event is SessionEvent.Observe) {
            val activity = event.activity
            if (old.activity == DeviceActivity.OFF && activity != DeviceActivity.OFF) {
                state = state.copy(resetEarned = state.resetEarned ||
                    (state.breakSince?.let { now - it >= config.resetMs } ?: false), breakSince = null)
            }
            if (activity == DeviceActivity.OFF && old.activity != DeviceActivity.OFF) state = state.copy(breakSince = now)
            state = state.copy(activity = activity)
            // Wake ends the continuous break and restores color even behind keyguard.
            // Also consume earned resets from checkpoints written by the prior version.
            if (activity != DeviceActivity.OFF && state.resetEarned) {
                effect = DisplayEffect.RESTORE
                purpose = OperationPurpose.RESET
                state = state.copy(hasSession = true, consumedMs = 0, exhausted = false, resetEarned = false, temporary = null)
            }
            if (activity == DeviceActivity.UNLOCKED && !state.hasSession) {
                state = state.copy(hasSession = true, consumedMs = 0, exhausted = false, resetEarned = false)
            }
        }
        if (state.hasSession && !state.exhausted && state.consumedMs >= config.allowanceMs) state = state.copy(exhausted = true)
        if (state.activity == DeviceActivity.UNLOCKED && state.hasSession) {
            val temporary = state.temporary
            if (temporary != null) {
                if (temporary.remaining(now) == 0L) {
                    state = state.copy(temporary = null)
                    effect = DisplayEffect.ENABLE
                    purpose = OperationPurpose.REACTIVATE
                }
            } else {
                state = state.copy(activeSince = now)
                if (state.exhausted && (!old.exhausted || old.activity != DeviceActivity.UNLOCKED)) {
                    effect = DisplayEffect.ENABLE
                    purpose = OperationPurpose.ACTIVATE
                }
            }
        }
        return SessionTransition(state, effect, purpose)
    }
}
