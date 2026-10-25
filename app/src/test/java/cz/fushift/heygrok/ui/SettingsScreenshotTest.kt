package cz.fushift.heygrok.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import cz.fushift.heygrok.WakePhrase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SettingsScreenshotTest(private val dark: Boolean) {

    @get:Rule
    val paparazzi = Paparazzi(
        // Tall enough to fit the whole scrolling screen in one image.
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            screenHeight = 2900,
            nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    @Test
    fun settings() {
        paparazzi.snapshot(name = if (dark) "dark" else "light") {
            HeyGrokTheme(darkTheme = dark) {
                SettingsScreen(
                    state = SettingsUiState(
                        listening = true,
                        running = true,
                        phrase = WakePhrase.HEY_GROK,
                        threshold = 0.9f,
                        score = 0.97f,
                        detected = true,
                        chime = true,
                        startOnBoot = false,
                        grokInstalled = true,
                    ),
                    onListeningChange = {},
                    onPhraseChange = {},
                    onThresholdChange = {},
                    onChimeChange = {},
                    onStartOnBootChange = {},
                    onOpenGrok = {},
                    onOpenOnboarding = {},
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}")
        fun modes() = listOf(false, true)
    }
}
