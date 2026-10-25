package cz.fushift.heygrok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cz.fushift.heygrok.ui.HeyGrokTheme
import cz.fushift.heygrok.ui.OnboardingScreen
import cz.fushift.heygrok.ui.SettingsRoute
import cz.fushift.heygrok.ui.isOnboardingDone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeyGrokTheme {
                HeyGrokApp()
            }
        }
    }
}

@Composable
private fun HeyGrokApp() {
    val context = LocalContext.current
    var showOnboarding by rememberSaveable { mutableStateOf(!isOnboardingDone(context)) }
    if (showOnboarding) {
        OnboardingScreen(onContinue = { showOnboarding = false })
    } else {
        SettingsRoute(onOpenOnboarding = { showOnboarding = true })
    }
}
