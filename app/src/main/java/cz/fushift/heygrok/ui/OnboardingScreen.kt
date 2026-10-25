package cz.fushift.heygrok.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import cz.fushift.heygrok.Prefs

private data class SetupStatus(
    val microphone: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val overlay: Boolean,
)

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun readStatus(context: Context) = SetupStatus(
    microphone = hasPermission(context, Manifest.permission.RECORD_AUDIO),
    notifications = Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
    battery = context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(context.packageName),
    overlay = Settings.canDrawOverlays(context),
)

fun isOnboardingDone(context: Context): Boolean {
    val status = readStatus(context)
    return status.microphone && (status.notifications || Prefs(context).notificationsAsked)
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readStatus(context)
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = readStatus(context)
    }
    BackHandler(enabled = status.microphone, onBack = onContinue)
    val packageUri = "package:${context.packageName}".toUri()

    Page {
        PageHeader("Set up")
        BodyText("Hey Grok listens for a wake phrase on this device and opens Grok when it hears it.")

        StepCard(
            title = "Microphone",
            done = status.microphone,
            optional = false,
            text = "Needed to hear the wake word. Audio is processed on this device and is never recorded or sent.",
            action = "Allow microphone",
            onAction = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
        )
        if (Build.VERSION.SDK_INT >= 33) {
            StepCard(
                title = "Notifications",
                done = status.notifications,
                optional = false,
                text = "Shows that listening is on, and lets you open Grok from a notification.",
                action = "Allow notifications",
                onAction = {
                    Prefs(context).notificationsAsked = true
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }
        StepCard(
            title = "Battery",
            done = status.battery,
            optional = true,
            text = "Lets listening continue while the screen is off. Some phones stop background apps anyway.",
            action = "Allow background use",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
            },
        )
        StepCard(
            title = "Open Grok from the background",
            done = status.overlay,
            optional = true,
            text = "Android blocks apps from opening other apps from the background. With \"Display over other apps\" " +
                "allowed, Grok opens directly. Without it, a detection shows a notification you tap to open Grok.",
            action = "Allow display over other apps",
            onAction = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)) },
        )

        PrimaryButton("Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth(), enabled = status.microphone)
    }
}

@Composable
private fun StepCard(
    title: String,
    done: Boolean,
    optional: Boolean,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    FlatCard(
        title = title,
        badge = {
            when {
                done -> StatusBadge("Done", BadgeTone.Positive)
                optional -> StatusBadge("Optional", BadgeTone.Info)
                else -> StatusBadge("Needed", BadgeTone.Attention)
            }
        },
    ) {
        Caption(text)
        if (!done) PrimaryButton(action, onClick = onAction)
    }
}
