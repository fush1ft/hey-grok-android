package cz.fushift.heygrok

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var phrase: WakePhrase
        get() = WakePhrase.fromKey(prefs.getString("wake_phrase", null))
        set(value) = prefs.edit { putString("wake_phrase", value.name) }

    fun threshold(phrase: WakePhrase): Float = prefs.getFloat(phrase.thresholdKey, phrase.defaultThreshold)

    fun setThreshold(phrase: WakePhrase, value: Float) = prefs.edit { putFloat(phrase.thresholdKey, value) }

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
