package app.ndplabs.phonefade

/** Android-free status shared by UI and notifications; display success requires verification. */
object MonitoringPresentation {
    fun status(state: MonitoringState): String = when {
        state.checkpoint.attention != null -> "Attention required"
        state.starting -> "Starting monitoring"
        !state.running -> if (state.checkpoint.session.enabled) "Monitoring interrupted" else "Monitoring disabled"
        state.checkpoint.pending != null -> "Updating display settings"
        state.checkpoint.session.temporary != null -> "Temporary color"
        state.display.ownsGrayscale -> "Grayscale active"
        state.checkpoint.session.activity == DeviceActivity.LOCKED -> "Waiting for unlock"
        state.checkpoint.session.activity == DeviceActivity.OFF -> "Usage paused"
        else -> "Monitoring active"
    }
    fun canUseTemporaryColor(state: MonitoringState) = state.ready && state.running &&
        state.checkpoint.attention == null && state.checkpoint.pending == null &&
        state.checkpoint.session.exhausted && state.checkpoint.session.temporary == null &&
        state.checkpoint.session.activity == DeviceActivity.UNLOCKED &&
        state.display.permissionGranted && state.display.ownsGrayscale

    fun remaining(state: MonitoringState, now: Long): Long = state.checkpoint.session.let {
        it.temporary?.remaining(now) ?: it.remaining(state.checkpoint.config, now)
    }

    fun counting(state: MonitoringState) = state.running && state.checkpoint.attention == null &&
        state.checkpoint.pending == null && state.checkpoint.session.let {
            it.temporary?.activeSince != null || (!it.exhausted && it.activeSince != null)
        }

    fun text(state: MonitoringState, now: Long, economical: Boolean = false): String {
        val cp = state.checkpoint
        if (cp.attention != null) return "Attention required — open Offramp Fade"
        if (state.starting) return "Starting monitoring…"
        if (!state.running) return if (cp.session.enabled) "Monitoring interrupted — enable to resume" else
            if (state.display.ownsGrayscale) "Manual grayscale active" else "Monitoring disabled"
        if (cp.pending != null) return when (cp.pendingPurpose) {
            OperationPurpose.TEMPORARY_COLOR -> "Restoring color…"
            OperationPurpose.RESET, OperationPurpose.DISABLE -> "Restoring original settings…"
            else -> "Applying grayscale…"
        }
        val temporary = cp.session.temporary
        val display = when {
            temporary != null && temporary.remaining(now) > 0 -> "Temporary color: ${CountdownFormatter.remaining(temporary.remaining(now), economical)}"
            temporary != null -> "Temporary color ended — grayscale pending unlock"
            state.display.ownsGrayscale -> "Grayscale active"
            cp.session.exhausted || cp.session.remaining(cp.config, now) == 0L ->
                if (cp.session.activity == DeviceActivity.UNLOCKED) "Applying grayscale…" else "Grayscale pending unlock"
            else -> "Color: ${CountdownFormatter.remaining(cp.session.remaining(cp.config, now), economical)}"
        }
        val pause = if (cp.session.activity == DeviceActivity.LOCKED && (!cp.session.exhausted || temporary != null))
            "$display · waiting for unlock"
        else if (cp.session.activity != DeviceActivity.UNLOCKED) "Paused — $display" else display
        return if (cp.interrupted) "$pause · recovered after interruption" else pause
    }

    fun breakMessage(config: MonitoringConfig) = "Turn the screen off for ${CountdownFormatter.minutes(config.resetMinutes)} uninterrupted. " +
        "Color returns when you wake it. A fresh allowance starts after unlock. Waking interrupts the break; short breaks do not add up."
}
