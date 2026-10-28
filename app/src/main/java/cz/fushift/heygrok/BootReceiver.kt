package cz.fushift.heygrok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        // A microphone service cannot be started from BOOT_COMPLETED (Android 14+) and would get no
        // mic access when started from the background before that, so ask the user to tap instead.
        if (prefs.startOnBoot && prefs.enabled) Notifications.showStartPrompt(context)
    }
}
