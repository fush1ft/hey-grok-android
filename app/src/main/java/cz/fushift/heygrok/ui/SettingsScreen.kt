package cz.fushift.heygrok.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.fushift.heygrok.GrokLauncher
import cz.fushift.heygrok.LiveState
import cz.fushift.heygrok.Prefs
import cz.fushift.heygrok.WakePhrase
import cz.fushift.heygrok.WakeWordService
import kotlinx.coroutines.delay
import java.util.Locale

private const val DETECTED_BADGE_MS = 2000L

data class SettingsUiState(
    val listening: Boolean,
    val running: Boolean,
    val phrase: WakePhrase,
    val threshold: Float,
    val score: Float,
    val detected: Boolean,
    val chime: Boolean,
    val startOnBoot: Boolean,
    val grokInstalled: Boolean,
)

@Composable
fun SettingsRoute(onOpenOnboarding: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var listening by remember { mutableStateOf(prefs.enabled) }
    var phrase by remember { mutableStateOf(prefs.phrase) }
    var threshold by remember { mutableFloatStateOf(prefs.threshold(phrase)) }
    var chime by remember { mutableStateOf(prefs.chime) }
    var startOnBoot by remember { mutableStateOf(prefs.startOnBoot) }
    var grokInstalled by remember { mutableStateOf(GrokLauncher.isGrokInstalled(context)) }
    LifecycleResumeEffect(Unit) {
        // The notification's Stop action changes this while we are in the background.
        listening = prefs.enabled
        // Restart after a reboot or process death; allowed now because the activity is visible.
        if (listening && !LiveState.running.value) {
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
        }
        grokInstalled = GrokLauncher.isGrokInstalled(context)
        onPauseOrDispose {}
    }

    val running by LiveState.running.collectAsStateWithLifecycle()
    val score by LiveState.score.collectAsStateWithLifecycle()
    val lastDetectionAt by LiveState.lastDetectionAt.collectAsStateWithLifecycle()
    var detected by remember { mutableStateOf(false) }
    LaunchedEffect(lastDetectionAt) {
        val remaining = lastDetectionAt + DETECTED_BADGE_MS - SystemClock.elapsedRealtime()
        detected = remaining > 0
        if (detected) {
            delay(remaining)
            detected = false
        }
    }

    SettingsScreen(
        state = SettingsUiState(
            listening = listening,
            running = running,
            phrase = phrase,
            threshold = threshold,
            score = score,
            detected = detected,
            chime = chime,
            startOnBoot = startOnBoot,
            grokInstalled = grokInstalled,
        ),
        onListeningChange = { on ->
            listening = on
            prefs.enabled = on
            val intent = Intent(context, WakeWordService::class.java)
            if (on) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        },
        onPhraseChange = {
            prefs.phrase = it
            phrase = it
            threshold = prefs.threshold(it)
        },
        onThresholdChange = {
            threshold = it
            prefs.setThreshold(phrase, it)
        },
        onChimeChange = {
            chime = it
            prefs.chime = it
        },
        onStartOnBootChange = {
            startOnBoot = it
            prefs.startOnBoot = it
        },
        onOpenGrok = { GrokLauncher.launch(context, inForeground = true) },
        onOpenOnboarding = onOpenOnboarding,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onListeningChange: (Boolean) -> Unit,
    onPhraseChange: (WakePhrase) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onChimeChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onOpenGrok: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Page {
        PageHeader("Wake word")
        BodyText("Say \"${state.phrase.label}\" to open Grok.")

        FlatCard(title = "Wake phrase") {
            SegmentedChoice(WakePhrase.entries, state.phrase, { it.label }, onPhraseChange)
            Caption(
                "Both models are for non-commercial use only. " +
                    "Hey Jarvis is openWakeWord's pretrained model, licensed CC BY-NC-SA 4.0.",
            )
        }

        FlatCard(
            title = "Listening",
            badge = {
                if (state.running) StatusBadge("Live", BadgeTone.Positive) else StatusBadge("Off", BadgeTone.Neutral)
            },
        ) {
            SwitchRow("Listen in the background", state.listening, onListeningChange)
        }

        FlatCard(
            title = "Sensitivity",
            badge = { MonoValue(state.threshold) },
        ) {
            Slider(
                value = state.threshold,
                onValueChange = onThresholdChange,
                valueRange = 0.10f..0.95f,
                colors = monochromeSliderColors(),
            )
            Caption("Lower = triggers more easily, more false alarms.")
            Caption(
                "Default ${String.format(Locale.ROOT, "%.2f", state.phrase.defaultThreshold)} for ${state.phrase.label}.",
            )
        }

        FlatCard(
            title = "Live test",
            badge = {
                if (state.detected) StatusBadge("Detected", BadgeTone.Positive) else StatusBadge("Waiting", BadgeTone.Neutral)
            },
        ) {
            ScoreMeter(state.score, state.threshold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption("Score")
                Spacer(Modifier.weight(1f))
                MonoValue(state.score)
            }
            if (!state.running) Caption("Turn listening on to see live scores.")
        }

        FlatCard(title = "Options") {
            SwitchRow("Chime on trigger", state.chime, onChimeChange)
            SwitchRow("Offer to start after reboot", state.startOnBoot, onStartOnBootChange)
            Caption(
                "Android 14 and later do not let apps start the microphone at boot. " +
                    "A notification appears after reboot instead; tap it to start listening.",
            )
        }

        FlatCard(
            title = "Grok Bot",
            badge = {
                if (state.grokInstalled) {
                    StatusBadge("Installed", BadgeTone.Positive)
                } else {
                    StatusBadge("Not installed", BadgeTone.Attention)
                }
            },
        ) {
            PrimaryButton("Open Grok now", onClick = onOpenGrok, enabled = state.grokInstalled)
        }

        QuietButton("Battery and permissions", onClick = onOpenOnboarding)
    }
}

@Composable
private fun MonoValue(value: Float) {
    Text(
        String.format(Locale.ROOT, "%.2f", value),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ScoreMeter(score: Float, threshold: Float) {
    val track = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.onSurface
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        drawRoundRect(fill, Offset(0f, top), Size(size.width * score.coerceIn(0f, 1f), barHeight), radius)
        val x = size.width * threshold
        drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

private val PreviewState = SettingsUiState(
    listening = true,
    running = true,
    phrase = WakePhrase.HEY_GROK,
    threshold = 0.9f,
    score = 0.97f,
    detected = true,
    chime = true,
    startOnBoot = false,
    grokInstalled = true,
)

@Composable
private fun SettingsPreviewContent() {
    HeyGrokTheme {
        SettingsScreen(PreviewState, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Light")
@Composable
private fun SettingsLightPreview() = SettingsPreviewContent()

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() = SettingsPreviewContent()
