package app.ndplabs.phonefade

/** Presentation only; callers supply monotonic remaining time, never a decrementing counter. */
object CountdownFormatter {
    fun minutes(value: Int): String = "$value ${if (value == 1) "minute" else "minutes"}"

    fun remaining(ms: Long, economical: Boolean = false): String {
        if (ms <= 0) return "Time exhausted"
        if (ms >= 60_000) return "${minutes(((ms - 1) / 60_000 + 1).toInt())} remaining"
        if (economical) return "Less than a minute remaining"
        // At 59.999 seconds clamp the rounded-up seconds to the requested 0:SS form.
        val seconds = ((ms - 1) / 1000 + 1).coerceIn(1, 59)
        return "0:${seconds.toString().padStart(2, '0')}"
    }

    fun nextRefresh(ms: Long): Long = when {
        ms <= 0 -> 60_000
        ms < 60_000 -> ((ms - 1) % 1000 + 1)
        ms == 60_000L -> 1
        else -> ((ms - 1) % 60_000 + 1)
    }
}
