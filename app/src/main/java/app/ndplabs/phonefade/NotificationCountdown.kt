package app.ndplabs.phonefade

/** An elapsed-time deadline is the publication identity; wall time is only an Android display adapter. */
data class NotificationPresentation(val text: String, val deadlineElapsed: Long? = null) {
    fun textAt(elapsedNow: Long): String = if (deadlineElapsed != null && deadlineElapsed <= elapsedNow)
        "Checking monitoring — open Offramp Fade" else text

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
        val label = if (session.temporary != null) "Temporary color" else "Color"
        if (counting && compatible && remaining in 1..60_000 && deadline != null && deadline > now) {
            return NotificationPresentation("$label: Less than a minute remaining", deadline)
        }
        // Keep notification copy compact; interruption history remains visible in the app.
        // A paused remainder is fixed, so seconds cannot become stale while paused.
        if (state.ready && state.running && session.enabled && !state.starting && cp.attention == null &&
            cp.pending == null && !state.display.ownsGrayscale && remaining > 0 &&
            (session.temporary != null || !session.exhausted)) {
            val time = CountdownFormatter.remaining(remaining, economical = counting)
            val remainingText = if (remaining < 60_000 && !counting) "$time remaining" else time
            val paused = if (!counting) " · Paused" else ""
            return NotificationPresentation("$label: $remainingText$paused")
        }
        if (state.running && !state.starting && cp.attention == null && cp.pending == null &&
            session.temporary == null && state.display.ownsGrayscale) {
            return NotificationPresentation("Grayscale active")
        }
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
