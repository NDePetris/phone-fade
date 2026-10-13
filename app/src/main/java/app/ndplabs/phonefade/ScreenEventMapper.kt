package app.ndplabs.phonefade

enum class ScreenSignal { SCREEN_OFF, SCREEN_ON, USER_PRESENT }

/** Reconcile screen boundaries, but retain the system's explicit unlock evidence. */
object ScreenEventMapper {
    fun event(signal: ScreenSignal, observed: DeviceActivity): SessionEvent {
        // USER_PRESENT means keyguard has gone. Its synchronous keyguard query can
        // still report LOCKED; do not let that snapshot veto the unlock broadcast.
        // An OFF snapshot, however, means delivery crossed a newer screen-off boundary.
        if (signal == ScreenSignal.USER_PRESENT && observed != DeviceActivity.OFF) {
            return SessionEvent.Observe(DeviceActivity.UNLOCKED)
        }
        // Never join off intervals or fabricate unlocked use across a missed boundary.
        val missedBoundary = (signal == ScreenSignal.SCREEN_ON && observed == DeviceActivity.OFF) ||
            (signal == ScreenSignal.SCREEN_OFF && observed != DeviceActivity.OFF)
        return if (missedBoundary) SessionEvent.Recover(observed) else SessionEvent.Observe(observed)
    }
}
