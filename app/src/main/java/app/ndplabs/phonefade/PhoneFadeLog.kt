package app.ndplabs.phonefade

import android.util.Log

/** Debug builds only. Log state flags and monotonic timing, never settings or user data. */
internal object PhoneFadeLog {
    var enabled = false

    fun debug(message: String) {
        if (enabled) Log.d("PhoneFade", message)
    }

    fun state(label: String, state: MonitoringState) {
        val cp = state.checkpoint
        val session = cp.session
        debug("$label running=${state.running} activity=${session.activity} " +
            "generation=${session.generation} exhausted=${session.exhausted} " +
            "resetEarned=${session.resetEarned} breakSince=${session.breakSince} " +
            "activeSince=${session.activeSince} observedAt=${session.observedAt} " +
            "ownsGrayscale=${state.display.ownsGrayscale} pending=${cp.pending} " +
            "attention=${cp.attention != null} interrupted=${cp.interrupted}")
    }
}
