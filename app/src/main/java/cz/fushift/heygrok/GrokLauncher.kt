package cz.fushift.heygrok

import android.content.Context
import android.content.Intent
import android.provider.Settings

object GrokLauncher {
    private const val GROK_PACKAGE = "ai.x.grok.bot"

    fun isGrokInstalled(context: Context): Boolean = grokIntent(context) != null

    /** Pass [inForeground] when called from a visible activity, which may always start activities. */
    fun launch(context: Context, inForeground: Boolean = false) {
        val intent = grokIntent(context)
        when {
            intent == null -> Notifications.showGrokMissing(context)
            // Overlay permission exempts the app from background activity launch restrictions.
            inForeground || Settings.canDrawOverlays(context) -> context.startActivity(intent)
            else -> Notifications.showOpenGrok(context, intent)
        }
    }

    private fun grokIntent(context: Context): Intent? {
        val pm = context.packageManager
        // Grok Bot 1.17.0 exports AssistTrampolineActivity with an ACTION_ASSIST filter; not a public API.
        val assist = Intent(Intent.ACTION_ASSIST).setPackage(GROK_PACKAGE)
        val intent = if (assist.resolveActivity(pm) != null) assist else pm.getLaunchIntentForPackage(GROK_PACKAGE)
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
