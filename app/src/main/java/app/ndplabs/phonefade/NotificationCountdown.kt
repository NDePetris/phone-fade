package app.ndplabs.phonefade

/** An elapsed-time deadline is the publication identity; wall time is only an Android display adapter. */
data class NotificationPresentation(val text: String, val deadlineElapsed: Long? = null) {
    fun textAt(elapsedNow: Long): String = if (deadlineElapsed != null && deadlineElapsed <= elapsedNow)
        "Scheduled end reached — checking monitoring" else text

    fun whenMillis(elapsedNow: Long, wallNow: Long): Long? = deadlineElapsed?.takeIf { it > elapsedNow }?.let {
        // Android's Chronometer rounds down. Round display seconds up; this does not
        // move the engine deadline or grant additional usage. Expiry replaces the view.
        wallNow + (it - elapsedNow) + 999L
    }
}

object NotificationCountdown {
    fun presentation(state: MonitoringState, now: Long): NotificationPresentation {
        val cp = state.checkpoint
        val session = cp.session
        val remaining = MonitoringPresentation.remaining(state, now)
        val counting = state.ready && session.enabled && session.activity == DeviceActivity.UNLOCKED &&
            !state.starting && MonitoringPresentation.counting(state)
        val compatible = state.display.readable && state.display.permissionGranted && state.display.canEnable &&
            (session.temporary == null || state.display.current == cp.expectedBaseline)
        val deadline = session.deadline(cp.config)
        if (counting && compatible && remaining in 1..60_000 && deadline != null && deadline > now) {
            val label = if (session.temporary != null) "Temporary color" else "Color allowance"
            val recovered = if (cp.interrupted) " · recovered after interruption" else ""
            return NotificationPresentation("$label · scheduled countdown$recovered", deadline)
        }
        // A paused remainder is fixed, so seconds cannot become stale while paused.
        val text = MonitoringPresentation.text(state, now, economical = counting)
        return NotificationPresentation(text)
    }

    /** One final-minute boundary update, existing minute checkpoints, and the engine deadline. */
    fun nextWakeDelay(deadline: Long, now: Long): Long {
        val remaining = (deadline - now).coerceAtLeast(0)
        val finalMinute = if (remaining > 60_000) remaining - 60_000 else Long.MAX_VALUE
        return minOf(60_000L, remaining, finalMinute)
    }
}
