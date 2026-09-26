package com.yuji.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yuji.app.R

enum class ThemeMode { DARK, LIGHT }

/**
 * Design tokens — "Minimalist Modern" in two brightnesses. One electric-blue accent with its
 * signature gradient (Accent → AccentBright), solid cards with hairline borders, Calistoga for
 * big figures, pill section labels. Light sits on warm off-white; dark on deep slate with a
 * dot texture. Green/red are reserved for up/down meaning.
 */
@Immutable
data class YujiColorScheme(
    val isLight: Boolean,
    /** Page canvas. */
    val Background: Color,
    /** App chrome: navigation bar and fixed bottom action bars. */
    val Chrome: Color,
    /** Opaque surfaces that must hide what is behind them: sheets, input wells. */
    val Card: Color,
    /** Dialogs, tooltips, lifted (dragged) rows. */
    val CardHigh: Color,
    /** Card fill: translucent glass in dark, solid white in light. */
    val Surface: Color,
    /** Small inner fills inside cards: chips, stat pills, tracks, avatar plates. */
    val Muted: Color,
    val MutedPressed: Color,
    /** Hairline borders and dividers. */
    val Outline: Color,
    /** Borders that need to be seen: inputs, pressed cards. */
    val OutlineStrong: Color,
    val Text: Color,
    val TextMuted: Color,
    val TextFaint: Color,
    /** Brand accent for fills, selection and glows; gradients run Accent → AccentBright. */
    val Accent: Color,
    val AccentBright: Color,
    /** Accent for text and icons on this canvas (meets 4.5:1). */
    val AccentText: Color,
    /** Tinted selection fill and its border. */
    val AccentSoft: Color,
    val AccentBorder: Color,
    /** Deep plate for white artwork (e.g. Iconify icons). */
    val InverseSurface: Color,
    /** Up / positive. */
    val Mint: Color,
    /** Down / negative / destructive. */
    val Coral: Color,
    val Amber: Color,
    val Blue: Color,
    /** Categorical colors for charts and letter avatars. */
    val Palette: List<Color>,
    /** Fill for page titles and big figures. */
    val Headline: Brush,
    /** Fill for the one headline word that carries the brand, e.g. "余记". */
    val AccentHeadline: Brush,
    /** Light overlay the press spotlight adds on top of the pressed surface. */
    val PressLift: Color,
)

/** Inter for Latin letters and digits; Chinese falls back to the system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Warm display serif for big figures (single weight). */
val Calistoga = FontFamily(Font(R.font.calistoga_regular, FontWeight.Normal))

/** Tabular, lining figures so amounts line up and don't jitter while typing. */
const val TNUM = "tnum, lnum"

object YujiColors {
    private val darkText = Color(0xFFF1F5F9)
    private val slate900 = Color(0xFF0F172A)

    val Dark = YujiColorScheme(
        isLight = false,
        Background = Color(0xFF0B1120),
        Chrome = Color(0xFF0F172A),
        Card = Color(0xFF131C2E),
        CardHigh = Color(0xFF1A2438),
        Surface = Color(0xFF131C2E),
        Muted = Color(0xFF1B2538),
        MutedPressed = Color(0xFF243049),
        Outline = Color(0xFF1E293B),
        OutlineStrong = Color(0xFF334155),
        Text = darkText,
        TextMuted = Color(0xFF94A3B8),
        TextFaint = Color(0xFF7B8BA3),
        Accent = Color(0xFF0052FF),
        AccentBright = Color(0xFF4D7CFF),
        AccentText = Color(0xFF7A9DFF),
        AccentSoft = Color(0xFF4D7CFF).copy(alpha = 0.14f),
        AccentBorder = Color(0xFF4D7CFF).copy(alpha = 0.40f),
        InverseSurface = Color(0xFF0F172A),
        Mint = Color(0xFF34D399),
        Coral = Color(0xFFF87171),
        Amber = Color(0xFFFBBF24),
        Blue = Color(0xFF60A5FA),
        Palette = listOf(
            Color(0xFF4D7CFF), Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFF34D399),
            Color(0xFFFBBF24), Color(0xFFF472B6), Color(0xFFA3E635), Color(0xFFFB923C),
        ),
        Headline = Brush.verticalGradient(listOf(darkText, darkText.copy(alpha = 0.82f))),
        AccentHeadline = Brush.horizontalGradient(listOf(Color(0xFF4D7CFF), Color(0xFF7A9DFF))),
        PressLift = Color.White.copy(alpha = 0.03f),
    )

    val Light = YujiColorScheme(
        isLight = true,
        Background = Color(0xFFFAFAFA),
        Chrome = Color(0xFFFFFFFF),
        Card = Color(0xFFFFFFFF),
        CardHigh = Color(0xFFFFFFFF),
        Surface = Color(0xFFFFFFFF),
        Muted = Color(0xFFF1F5F9),
        MutedPressed = Color(0xFFE2E8F0),
        Outline = Color(0xFFE2E8F0),
        OutlineStrong = Color(0xFFCBD5E1),
        Text = slate900,
        TextMuted = Color(0xFF64748B),
        TextFaint = Color(0xFF7B8798),
        Accent = Color(0xFF0052FF),
        AccentBright = Color(0xFF4D7CFF),
        AccentText = Color(0xFF0052FF),
        AccentSoft = Color(0xFF0052FF).copy(alpha = 0.07f),
        AccentBorder = Color(0xFF0052FF).copy(alpha = 0.30f),
        InverseSurface = slate900,
        Mint = Color(0xFF047857),
        Coral = Color(0xFFDC2626),
        Amber = Color(0xFFB45309),
        Blue = Color(0xFF2563EB),
        Palette = listOf(
            Color(0xFF0052FF), Color(0xFF7C3AED), Color(0xFF0891B2), Color(0xFF059669),
            Color(0xFFD97706), Color(0xFFDB2777), Color(0xFF65A30D), Color(0xFFEA580C),
        ),
        Headline = Brush.verticalGradient(listOf(slate900, Color(0xFF334155))),
        AccentHeadline = Brush.horizontalGradient(listOf(Color(0xFF0052FF), Color(0xFF4D7CFF))),
        PressLift = Color.Black.copy(alpha = 0.025f),
    )

    fun of(mode: ThemeMode) = if (mode == ThemeMode.LIGHT) Light else Dark
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

fun trendFor(c: YujiColorScheme, greenUp: Boolean) =
    if (greenUp) Trend(c.Mint, c.Coral, c.TextMuted) else Trend(c.Coral, c.Mint, c.TextMuted)

val LocalTrend = staticCompositionLocalOf { trendFor(YujiColors.Dark, true) }
val LocalGreenUp = staticCompositionLocalOf { true }
val LocalHideAmounts = staticCompositionLocalOf { false }

/** Motion tokens: quick and decisive, never bouncy. */
object Motion {
    val ExpoOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val QUICK = 200
    const val STANDARD = 300
}

private fun materialColors(c: YujiColorScheme): ColorScheme {
    val base = if (c.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = c.Accent,
        onPrimary = Color.White,
        primaryContainer = c.AccentSoft,
        onPrimaryContainer = c.AccentText,
        secondary = c.AccentText,
        onSecondary = c.Background,
        secondaryContainer = c.Muted,
        onSecondaryContainer = c.AccentText,
        background = c.Background,
        onBackground = c.Text,
        surface = c.Background,
        onSurface = c.Text,
        surfaceVariant = c.Card,
        onSurfaceVariant = c.TextMuted,
        surfaceContainerLowest = c.Chrome,
        surfaceContainerLow = c.Card,
        surfaceContainer = c.Card,
        surfaceContainerHigh = c.CardHigh,
        surfaceContainerHighest = c.CardHigh,
        outline = c.OutlineStrong,
        outlineVariant = c.Outline,
        error = c.Coral,
    )
}

private val base = Typography()

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val typography = Typography(
    displayLarge = base.displayLarge.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM),
    displayMedium = base.displayMedium.inter().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em),
    displaySmall = base.displaySmall.inter().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM),
    headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.01).em),
    titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    bodyLarge = base.bodyLarge.inter().copy(fontFeatureSettings = TNUM),
    bodyMedium = base.bodyMedium.inter().copy(fontFeatureSettings = TNUM),
    bodySmall = base.bodySmall.inter().copy(fontFeatureSettings = TNUM),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.inter(),
    labelSmall = base.labelSmall.inter(),
)

object Amount {
    /** The screen's headline figure, in the display serif. */
    val hero = TextStyle(fontFamily = Calistoga, fontSize = 40.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM)
    val large = TextStyle(fontFamily = Calistoga, fontSize = 28.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM)
    val row = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val small = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontFeatureSettings = TNUM)
}

object YujiType {
    /** Small section tag above a group of cards or inside a card. */
    val tag = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em)
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
private fun rippleFor(c: YujiColorScheme) = RippleConfiguration(
    color = c.AccentText,
    rippleAlpha = RippleAlpha(pressedAlpha = 0.12f, focusedAlpha = 0.12f, draggedAlpha = 0.08f, hoveredAlpha = 0.06f),
)

@Composable
fun YujiTheme(mode: ThemeMode, greenUp: Boolean, hideAmounts: Boolean, content: @Composable () -> Unit) {
    val c = YujiColors.of(mode)
    MaterialTheme(colorScheme = materialColors(c), typography = typography, shapes = shapes) {
        // Provided inside MaterialTheme, which would otherwise install its own ripple as LocalIndication.
        ProvideScheme(c, greenUp) {
            CompositionLocalProvider(LocalHideAmounts provides hideAmounts, content = content)
        }
    }
}

/**
 * Installs a color scheme for everything below this point: tokens, content color, trend
 * colors, press feedback and ripples.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideScheme(c: YujiColorScheme, greenUp: Boolean = LocalGreenUp.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalYujiColors provides c,
        LocalContentColor provides c.Text,
        LocalGreenUp provides greenUp,
        LocalTrend provides trendFor(c, greenUp),
        LocalIndication provides SpotlightIndication(c.Accent, c.PressLift),
        LocalRippleConfiguration provides rippleFor(c),
        content = content,
    )
}
