package app.ndplabs.phonefade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object MonitoringNotification {
    const val ID = 1
    const val CHANNEL = "monitoring"

    fun visible(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun create(context: Context, presentation: NotificationPresentation): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automatic monitoring", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val elapsedNow = SystemClock.elapsedRealtime()
        val whenMillis = presentation.whenMillis(elapsedNow, System.currentTimeMillis())
        val text = presentation.textAt(elapsedNow)
        val countdown = whenMillis != null
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name)).setContentText(text).setContentIntent(open)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(countdown)
            .setUsesChronometer(countdown).setChronometerCountDown(countdown)
        whenMillis?.let { builder.setWhen(it) }
        return builder.build()
    }

    fun text(state: MonitoringState, now: Long): String = NotificationCountdown.presentation(state, now).text
}
