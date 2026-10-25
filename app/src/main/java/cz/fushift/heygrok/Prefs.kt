package cz.fushift.heygrok

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    // Keyed per model so a value tuned for the old placeholder model does not carry over.
    var threshold: Float
        get() = prefs.getFloat("threshold_hey_grok", WakeModel.DEFAULT_THRESHOLD)
        set(value) = prefs.edit { putFloat("threshold_hey_grok", value) }

    var chime: Boolean
        get() = prefs.getBoolean("chime", true)
        set(value) = prefs.edit { putBoolean("chime", value) }

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit { putBoolean("start_on_boot", value) }

    var notificationsAsked: Boolean
        get() = prefs.getBoolean("notifications_asked", false)
        set(value) = prefs.edit { putBoolean("notifications_asked", value) }
}
