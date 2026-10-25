package cz.fushift.heygrok

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

object GrokLauncher {
    private const val GROK_PACKAGE = "ai.x.grok.bot"

    fun isGrokInstalled(context: Context): Boolean = grokIntent(context) != null

    /** Pass [inForeground] when called from a visible activity, which may always start activities. */
    fun launch(context: Context, inForeground: Boolean = false) {
        val intent = grokIntent(context)
        // Overlay permission exempts the app from background activity launch restrictions.
        val overlay = Settings.canDrawOverlays(context)
        when {
            intent == null -> Notifications.showGrokMissing(context)
            inForeground -> context.startActivity(intent)
            isLockedOrScreenOff(context) -> when {
                Notifications.canUseFullScreenIntent(context) -> Notifications.showUnlockFullScreen(context)
                overlay -> context.startActivity(UnlockActivity.intent(context))
                else -> Notifications.showOpenGrok(context)
            }
            overlay -> context.startActivity(intent)
            else -> Notifications.showOpenGrok(context)
        }
    }

    internal fun grokIntent(context: Context): Intent? {
        val pm = context.packageManager
        // Grok Bot 1.17.0 has an ACTION_ASSIST AssistTrampolineActivity but ships it enabled="false", so this
        // resolves only if Grok enables it at runtime. Not a public API.
        val assist = Intent(Intent.ACTION_ASSIST).setPackage(GROK_PACKAGE)
        val intent = if (assist.resolveActivity(pm) != null) assist else pm.getLaunchIntentForPackage(GROK_PACKAGE)
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun isLockedOrScreenOff(context: Context): Boolean =
        context.getSystemService<KeyguardManager>()!!.isKeyguardLocked ||
            !context.getSystemService<PowerManager>()!!.isInteractive
}
