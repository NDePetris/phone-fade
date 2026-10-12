package app.ndplabs.phonefade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Credential-encrypted journal is available at BOOT_COMPLETED, after the first unlock. */
class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as PhoneFadeApplication
        app.submit {
            try { app.coordinator.bootRecovery(app.bootCount()) }
            finally { pending.finish() }
        }
    }
}
