package app.ndplabs.phonefade

data class MonitoringConfig(val allowanceMinutes: Int = 10, val resetMinutes: Int = 5) {
    init { require(allowanceMinutes in 1..120 && resetMinutes in 1..120) }
    val allowanceMs get() = allowanceMinutes * 60_000L
    val resetMs get() = resetMinutes * 60_000L
}

enum class DeviceActivity { OFF, LOCKED, UNLOCKED }
enum class DisplayEffect { ENABLE, RESTORE }

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
) {
    fun consumedAt(now: Long): Long = consumedMs + (activeSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remaining(config: MonitoringConfig, now: Long): Long =
        if (exhausted) 0 else (config.allowanceMs - consumedAt(now)).coerceAtLeast(0)
    fun deadline(config: MonitoringConfig): Long? =
        if (enabled && !exhausted && activeSince != null) activeSince + (config.allowanceMs - consumedMs).coerceAtLeast(0) else null
}

sealed interface SessionEvent {
    data class Enable(val activity: DeviceActivity) : SessionEvent
    data object Disable : SessionEvent
    data class Observe(val activity: DeviceActivity) : SessionEvent
    data class Deadline(val generation: Long) : SessionEvent
    data object ConfigurationChanged : SessionEvent
    data class Recover(val activity: DeviceActivity) : SessionEvent
}

data class SessionTransition(val state: SessionState, val effect: DisplayEffect? = null)

/** Android-free reducer. Timestamps are supplied by one monotonic clock. */
object SessionEngine {
    fun reduce(old: SessionState, event: SessionEvent, now: Long, config: MonitoringConfig): SessionTransition {
        require(now >= 0)
        if (event is SessionEvent.Deadline && event.generation != old.generation) return SessionTransition(old)
        if (event == SessionEvent.Disable) return SessionTransition(
            SessionState(generation = old.generation + 1, observedAt = now), DisplayEffect.RESTORE,
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
                state = state.copy(hasSession = true, consumedMs = 0, exhausted = false, resetEarned = false)
            }
            if (activity == DeviceActivity.UNLOCKED && !state.hasSession) {
                state = state.copy(hasSession = true, consumedMs = 0, exhausted = false, resetEarned = false)
            }
        }
        if (state.hasSession && !state.exhausted && state.consumedMs >= config.allowanceMs) state = state.copy(exhausted = true)
        if (state.activity == DeviceActivity.UNLOCKED && state.hasSession) {
            state = state.copy(activeSince = now)
            if (state.exhausted && (!old.exhausted || old.activity != DeviceActivity.UNLOCKED)) effect = DisplayEffect.ENABLE
        }
        return SessionTransition(state, effect)
    }
}
