package cz.fushift.heygrok.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cz.fushift.heygrok.R

/** Pastel pairs reserved for badges and status. */
@Immutable
data class StatusColors(
    val green: Color,
    val onGreen: Color,
    val amber: Color,
    val onAmber: Color,
    val blue: Color,
    val onBlue: Color,
)

private val LightStatus = StatusColors(
    green = Color(0xFFEDF3EC),
    onGreen = Color(0xFF2F5D37),
    amber = Color(0xFFFBF3DB),
    onAmber = Color(0xFF6B5310),
    blue = Color(0xFFE1F3FE),
    onBlue = Color(0xFF1F4F6B),
)

private val DarkStatus = StatusColors(
    green = Color(0xFF16241A),
    onGreen = Color(0xFF8FCB9A),
    amber = Color(0xFF2A2210),
    onAmber = Color(0xFFE4C874),
    blue = Color(0xFF12293A),
    onBlue = Color(0xFF8FD1F5),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private fun scheme(dark: Boolean, bg: Color, surface: Color, border: Color, text: Color, muted: Color) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = text,
        onPrimary = bg,
        secondary = muted,
        onSecondary = bg,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = muted,
        surfaceTint = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = border,
        outlineVariant = border,
    )

private val LightColors = scheme(
    dark = false,
    bg = Color(0xFFF7F6F3),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFEAEAEA),
    text = Color(0xFF111111),
    muted = Color(0xFF62615E),
)

private val DarkColors = scheme(
    dark = true,
    bg = Color(0xFF15140F),
    surface = Color(0xFF1C1B17),
    border = Color(0xFF302E27),
    text = Color(0xFFF2F1EC),
    muted = Color(0xFF9A988F),
)

private val InstrumentSerif = FontFamily(Font(R.font.instrument_serif, FontWeight.Normal))

private val Label = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.05.em)

private val HeyGrokTypography = Typography(
    displayLarge = TextStyle(fontFamily = InstrumentSerif, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = InstrumentSerif, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.6.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = Label.copy(fontSize = 13.sp),
    labelMedium = Label.copy(fontSize = 12.sp),
    labelSmall = Label,
)

private val HeyGrokShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

@Composable
fun HeyGrokTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HeyGrokTypography,
            shapes = HeyGrokShapes,
            content = content,
        )
    }
}
