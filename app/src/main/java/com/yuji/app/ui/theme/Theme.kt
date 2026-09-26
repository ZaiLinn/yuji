package com.yuji.app.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { DARK, LIGHT }

@Immutable
data class YujiColorScheme(
    val Background: Color,
    val Card: Color,
    val CardHigh: Color,
    val Outline: Color,
    val Text: Color,
    val TextMuted: Color,
    val TextFaint: Color,
    val Mint: Color,
    val Coral: Color,
    val Amber: Color,
    val Blue: Color,
    val Palette: List<Color>,
)

object YujiColors {
    /** Categorical palette for charts/avatars — dark theme (vibrant, for dark backgrounds). */
    private val DarkPalette = listOf(
        Color(0xFF3DDC97), Color(0xFF5B9DFF), Color(0xFFF5B942), Color(0xFFB57BFF),
        Color(0xFFFF8A5B), Color(0xFF4FD1E8), Color(0xFFFF6B9A), Color(0xFFA3D65C),
    )

    /** Categorical palette for light theme — darker/more saturated for white backgrounds. */
    private val LightPalette = listOf(
        Color(0xFF1A8A5E), Color(0xFF1E5EC9), Color(0xFFB07A15), Color(0xFF7B3FBF),
        Color(0xFFD4511E), Color(0xFF0E8CA8), Color(0xFFD43A6E), Color(0xFF5A8A1E),
    )

    /** Kept for any code that still references the static palette directly. */
    val Palette = DarkPalette

    val Dark = YujiColorScheme(
        Background = Color(0xFF0B0D0F),
        Card      = Color(0xFF15181B),
        CardHigh  = Color(0xFF1D2126),
        Outline   = Color(0xFF262B31),
        Text      = Color(0xFFECEFF1),
        TextMuted = Color(0xFFB0BCC5),
        TextFaint = Color(0xFF848E96),
        Mint      = Color(0xFF3DDC97),
        Coral     = Color(0xFFFF6B5E),
        Amber     = Color(0xFFF5B942),
        Blue      = Color(0xFF5B9DFF),
        Palette   = DarkPalette,
    )

    val Light = YujiColorScheme(
        Background = Color(0xFFF4F6F9),
        Card      = Color(0xFFFFFFFF),
        CardHigh  = Color(0xFFEAECF2),
        Outline   = Color(0xFFD1D5DB),
        Text      = Color(0xFF0D1117),
        TextMuted = Color(0xFF5C6470),
        TextFaint = Color(0xFF9AA1AB),
        Mint      = Color(0xFF1A8A5E),
        Coral     = Color(0xFFC9392D),
        Amber     = Color(0xFFB07A15),
        Blue      = Color(0xFF1E5EC9),
        Palette   = LightPalette,
    )
}

val LocalYujiColors = staticCompositionLocalOf { YujiColors.Dark }

@Immutable
data class Trend(val up: Color, val down: Color, val neutral: Color) {
    fun of(sign: Int): Color = when {
        sign > 0 -> up
        sign < 0 -> down
        else     -> neutral
    }
}

val LocalTrend = staticCompositionLocalOf { Trend(YujiColors.Dark.Mint, YujiColors.Dark.Coral, YujiColors.Dark.TextMuted) }
val LocalHideAmounts = staticCompositionLocalOf { false }

private val darkScheme = darkColorScheme(
    primary = YujiColors.Dark.Mint,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF12352A),
    onPrimaryContainer = YujiColors.Dark.Mint,
    secondary = YujiColors.Dark.Blue,
    secondaryContainer = YujiColors.Dark.CardHigh,
    onSecondaryContainer = YujiColors.Dark.Mint,
    background = YujiColors.Dark.Background,
    onBackground = YujiColors.Dark.Text,
    surface = YujiColors.Dark.Background,
    onSurface = YujiColors.Dark.Text,
    surfaceVariant = YujiColors.Dark.Card,
    onSurfaceVariant = YujiColors.Dark.TextMuted,
    surfaceContainerLowest = YujiColors.Dark.Background,
    surfaceContainerLow = YujiColors.Dark.Card,
    surfaceContainer = YujiColors.Dark.Card,
    surfaceContainerHigh = YujiColors.Dark.CardHigh,
    surfaceContainerHighest = Color(0xFF252A30),
    outline = YujiColors.Dark.Outline,
    outlineVariant = YujiColors.Dark.Outline,
    error = YujiColors.Dark.Coral,
)

private val lightScheme = lightColorScheme(
    primary = YujiColors.Light.Mint,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7EFD9),
    onPrimaryContainer = Color(0xFF002816),
    secondary = YujiColors.Light.Blue,
    secondaryContainer = YujiColors.Light.CardHigh,
    onSecondaryContainer = YujiColors.Light.Mint,
    background = YujiColors.Light.Background,
    onBackground = YujiColors.Light.Text,
    surface = YujiColors.Light.Background,
    onSurface = YujiColors.Light.Text,
    surfaceVariant = YujiColors.Light.Card,
    onSurfaceVariant = YujiColors.Light.TextMuted,
    surfaceContainerLowest = YujiColors.Light.Card,
    surfaceContainerLow = YujiColors.Light.Background,
    surfaceContainer = YujiColors.Light.Card,
    surfaceContainerHigh = YujiColors.Light.CardHigh,
    surfaceContainerHighest = Color(0xFFE0E3EB),
    outline = YujiColors.Light.Outline,
    outlineVariant = YujiColors.Light.Outline,
    error = YujiColors.Light.Coral,
)

private val base = Typography()

/** Tabular figures so amounts line up and don't jitter while typing. */
const val TNUM = "tnum"

private val typography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFeatureSettings = TNUM),
    bodyMedium = base.bodyMedium.copy(fontFeatureSettings = TNUM),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

object Amount {
    val hero = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM)
    val large = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val row = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val small = TextStyle(fontSize = 12.sp, fontFeatureSettings = TNUM)
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YujiTheme(themeMode: ThemeMode = ThemeMode.DARK, greenUp: Boolean, hideAmounts: Boolean, content: @Composable () -> Unit) {
    val dark = themeMode == ThemeMode.DARK
    val colors = if (dark) YujiColors.Dark else YujiColors.Light
    val scheme = if (dark) darkScheme else lightScheme
    val trend = if (greenUp) Trend(colors.Mint, colors.Coral, colors.TextMuted) else Trend(colors.Coral, colors.Mint, colors.TextMuted)
    CompositionLocalProvider(
        LocalYujiColors provides colors,
        LocalTrend provides trend,
        LocalHideAmounts provides hideAmounts,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
