package app.ndplabs.phonefade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object MonitoringNotification {
    const val ID = 1
    const val CHANNEL = "monitoring"

    fun visible(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun create(context: Context, text: String): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automatic monitoring", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Phone Fade").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).build()
    }

    fun text(state: MonitoringState, now: Long): String {
        val cp = state.checkpoint
        if (cp.attention != null) return "Attention required — open Phone Fade"
        if (!state.running) return if (cp.session.enabled) "Monitoring interrupted — enable to resume" else "Monitoring stopped"
        val display = if (state.display.ownsGrayscale) "Grayscale active" else if (cp.session.exhausted) "Grayscale pending unlock"
        else "Color: ${(cp.session.remaining(cp.config, now) + 59_999) / 60_000} minutes remaining"
        val pause = if (cp.session.activity != DeviceActivity.UNLOCKED) "Paused — $display" else display
        return if (cp.interrupted) "$pause · recovered after interruption" else pause
    }
}
