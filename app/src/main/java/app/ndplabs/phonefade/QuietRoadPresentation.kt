package app.ndplabs.phonefade

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** In-app copy only. Notification wording and timing remain independently owned. */
data class QuietRoadHero(val status: String, val title: String, val countdown: String? = null,
    val guidance: String, val primary: HeroAction? = null, val secondary: HeroAction? = null,
    val history: String? = null)

enum class HeroAction(val label: String) {
    START("Enable monitoring"), RESUME("Resume monitoring"), TEMPORARY("Use temporary color"),
    DISABLE("Disable and restore"), RESTORE("Restore original settings")
}

object QuietRoadPresentation {
    fun automaticBreakGuidance(resetMinutes: Int) = "Turn your screen off for $resetMinutes uninterrupted " +
        "${if (resetMinutes == 1) "minute" else "minutes"} to earn a reset. Color returns when you wake it."

    fun fieldSupportingText(value: String, active: Int): String? {
        val messages = buildList {
            if (value.toIntOrNull() !in 1..120) add("Enter a whole number from 1 to 120.")
            if (value.toIntOrNull() != active) add("Active: ${CountdownFormatter.minutes(active)}")
        }
        return messages.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun spokenCountdown(value: String): String {
        if (!value.startsWith("0:")) return value
        val seconds = value.substringAfter(':').toInt()
        return "$seconds ${if (seconds == 1) "second" else "seconds"}"
    }

    fun hero(state: MonitoringState, now: Long): QuietRoadHero {
        val cp = state.checkpoint
        val enabled = state.running || state.starting || cp.session.enabled
        val recovery = if (enabled) HeroAction.DISABLE else HeroAction.RESTORE
        val history = if (cp.interrupted) "Some screen time may not have been counted." else null
        fun result(status: String, title: String, guidance: String, countdown: String? = null,
            primary: HeroAction? = null, secondary: HeroAction? = null) =
            QuietRoadHero(status, title, countdown, guidance, primary, secondary, history)
        if (cp.attention != null) return result("Attention required", "Review display settings",
            "Recovery is not complete. Review the issue below and the retained recovery instructions.", primary = recovery)
        if (state.starting) return result("Starting monitoring", "Starting monitoring…",
            "Waiting for Android to start monitoring.", secondary = recovery)
        if (!state.running) {
            if (cp.session.enabled) return result("Monitoring interrupted", "Monitoring interrupted",
                "Confirmed usage is kept. Resume monitoring to continue, or disable and restore.",
                primary = HeroAction.RESUME, secondary = HeroAction.DISABLE)
            if (state.display.ownsGrayscale) return result("Monitoring disabled", "Manual grayscale active",
                "Automatic breaks are off. Restore original settings when you are ready.",
                primary = HeroAction.RESTORE, secondary = HeroAction.START)
            if (state.display.original != null) return result("Recovery available", "Review display settings",
                "Saved original settings are retained. Review recovery and diagnostics before continuing.", primary = HeroAction.RESTORE)
            return result("Monitoring disabled", "A quieter route awaits",
                "Enable monitoring to start your color allowance. Counts only while unlocked.", primary = HeroAction.START)
        }
        if (cp.pending != null) return result("Updating display settings", when (cp.pendingPurpose) {
            OperationPurpose.TEMPORARY_COLOR, OperationPurpose.RESET, OperationPurpose.DISABLE -> "Restoring original settings…"
            else -> "Applying grayscale…"
        }, "Wait for settings verification to complete.", secondary = recovery)
        val status = when (cp.session.activity) {
            DeviceActivity.LOCKED -> "Waiting for unlock"
            DeviceActivity.OFF -> "Usage paused"
            DeviceActivity.UNLOCKED -> "Monitoring active"
        }
        if (state.display.ownsGrayscale) return result(status, "Grayscale active",
            automaticBreakGuidance(cp.config.resetMinutes),
            primary = HeroAction.TEMPORARY.takeIf { MonitoringPresentation.canUseTemporaryColor(state) }, secondary = recovery)
        val temporary = cp.session.temporary != null
        val remaining = MonitoringPresentation.remaining(state, now)
        if (remaining <= 0 || (!temporary && cp.session.exhausted)) return result(status,
            if (cp.session.activity == DeviceActivity.UNLOCKED) "Applying grayscale…" else "Grayscale pending unlock",
            if (temporary) "Temporary color has ended. Your ordinary allowance remains exhausted."
            else "Your color allowance is used up.", secondary = recovery)
        val guidance = when {
            temporary -> "Counts only while unlocked. This doesn't reset your color allowance."
            cp.session.activity == DeviceActivity.LOCKED -> "Unlock to resume counting. Short breaks keep your remaining time."
            cp.session.activity == DeviceActivity.OFF -> "Counting resumes after unlock. Short breaks keep your remaining time."
            else -> "Counts only while unlocked. Short breaks keep your remaining time."
        }
        return result(status, if (temporary) "Temporary color remaining" else "Color remaining", guidance,
            CountdownFormatter.remaining(remaining).removeSuffix(" remaining"), secondary = recovery)
    }
}

data class TimingDraft(val allowance: String, val reset: String, val temporary: String) {
    fun config(): MonitoringConfig? {
        val values = listOf(allowance, reset, temporary).map { it.toIntOrNull() }
        return if (values.all { it in 1..120 }) MonitoringConfig(values[0]!!, values[1]!!, values[2]!!) else null
    }
    fun changed(saved: MonitoringConfig) = config() != saved
    fun canSave(saved: MonitoringConfig, available: Boolean) = available && config() != null && changed(saved)
}

enum class CommandFeedback(val message: String?) {
    SAVED("Settings saved."), RESTORED("Original settings restored. Check your display."),
    NOTHING_TO_RESTORE("No saved settings to restore."), FAILED(null)
}

/** ViewModel-owned mailbox: results are transient, buffered, and consumed once. */
class CommandFeedbackQueue {
    private val channel = Channel<CommandFeedback>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()
    fun offer(result: CommandFeedback) {
        if (result.message != null) channel.trySend(result)
    }
}
