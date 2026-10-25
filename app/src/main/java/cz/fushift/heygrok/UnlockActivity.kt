package cz.fushift.heygrok

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.getSystemService

/** Invisible activity that wakes the screen, asks for unlock and then opens Grok, which cannot show when locked. */
class UnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The manifest attributes showWhenLocked and turnScreenOn need API 27.
        if (Build.VERSION.SDK_INT < 27) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        Notifications.cancelWake(this)
        val keyguard = getSystemService<KeyguardManager>()!!
        if (!keyguard.isKeyguardLocked) {
            openGrok()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = openGrok()
                override fun onDismissCancelled() = finish()
                override fun onDismissError() = finish()
            },
        )
    }

    private fun openGrok() {
        val intent = GrokLauncher.grokIntent(this)
        if (intent != null) startActivity(intent) else Notifications.showGrokMissing(this)
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
