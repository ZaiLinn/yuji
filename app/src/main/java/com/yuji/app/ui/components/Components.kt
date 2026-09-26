package com.yuji.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.yuji.app.data.db.IconType
import com.yuji.app.domain.Money
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.Motion
import com.yuji.app.ui.theme.YujiColorScheme
import com.yuji.app.ui.theme.YujiType
import java.io.File
import java.math.BigDecimal

const val MASK = "••••••"

enum class CardVariant {
    /** Solid card with a hairline border (and a soft shadow in light). */
    Default,
    /** Tinted, for a card nested in a busy area. */
    Raised,
    /**
     * Featured: the one card a screen is about. Same surface, but framed by the signature
     * gradient stroke, washed with a hint of accent and lifted on an accent-tinted shadow.
     */
    Accent,
}

/**
 * The app's card. Clickable cards press down to 98% and light up under the finger
 * (see SpotlightIndication).
 */
@Composable
fun YujiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: CardVariant = CardVariant.Default,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalYujiColors.current
    val shape = MaterialTheme.shapes.large
    val featured = variant == CardVariant.Accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(Motion.QUICK, easing = Motion.ExpoOut), label = "card-scale")
    val elevation by animateDpAsState(
        when {
            featured -> if (pressed) 6.dp else 12.dp
            !c.isLight || variant == CardVariant.Raised -> 0.dp
            pressed -> 1.dp
            else -> 3.dp
        },
        tween(Motion.QUICK, easing = Motion.ExpoOut), label = "card-elevation",
    )
    val border by animateColorAsState(if (pressed) c.AccentBorder else c.Outline, tween(Motion.QUICK), label = "card-border")
    val shadowColor = if (featured) c.Accent else c.Text
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor) else Modifier)
            .clip(shape)
            .background(if (variant == CardVariant.Raised) c.Muted else c.Surface)
            .then(
                if (featured) {
                    Modifier.featuredWash(c).border(1.5.dp, Brush.linearGradient(listOf(c.Accent, c.AccentBright, c.Accent)), shape)
                } else {
                    Modifier.border(1.dp, border, shape)
                },
            )
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Accent wash for featured cards: a faint diagonal tint plus a glow pooled in the top-right corner. */
private fun Modifier.featuredWash(c: YujiColorScheme): Modifier = drawWithCache {
    val wash = Brush.linearGradient(
        listOf(c.Accent.copy(alpha = if (c.isLight) 0.05f else 0.10f), Color.Transparent),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val glow = Brush.radialGradient(
        listOf(c.AccentBright.copy(alpha = if (c.isLight) 0.12f else 0.22f), Color.Transparent),
        center = Offset(size.width, 0f), radius = size.width * 0.7f,
    )
    onDrawBehind {
        drawRect(wash)
        drawRect(glow)
    }
}

/** Label above a group of cards, e.g. "币种分布". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        action()
    }
}

/** Section label: an accent pill with a dot, the recurring section marker. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalYujiColors.current
    Row(
        modifier
            .clip(CircleShape)
            .background(c.AccentSoft)
            .border(1.dp, c.AccentBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text, style = YujiType.tag, color = c.AccentText)
    }
}

/** Page title with the theme's headline fill; [brand] uses the accent gradient. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, brand: Boolean = false) {
    val c = LocalYujiColors.current
    BasicText(text, modifier, style = MaterialTheme.typography.headlineSmall.copy(brush = if (brand) c.AccentHeadline else c.Headline))
}

/** CNY amount that respects the global "hide amounts" switch. A brush in [style] wins over the default color. */
@Composable
fun CnyText(
    value: BigDecimal?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
    compact: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val text = when {
        value == null -> "—"
        LocalHideAmounts.current -> MASK
        signed -> Money.signedCny(value)
        compact -> Money.compactCny(value)
        else -> Money.cny(value)
    }
    if (style.brush != null && color == Color.Unspecified) {
        // Material Text merges a color over the style and would drop the brush.
        BasicText(text, modifier, style = style.copy(textAlign = textAlign ?: style.textAlign), maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        val resolved = if (color == Color.Unspecified) LocalYujiColors.current.Text else color
        Text(text, style = style, color = resolved, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}

@Composable
fun NativeAmountText(value: BigDecimal, currency: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val resolvedColor = if (color == Color.Unspecified) LocalYujiColors.current.TextMuted else color
    Text(
        if (LocalHideAmounts.current) "$MASK $currency" else Money.amount(value, currency),
        style = style, color = resolvedColor, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Signed change colored by the up/down convention. */
@Composable
fun DeltaText(value: BigDecimal?, style: TextStyle, modifier: Modifier = Modifier) {
    val color = if (value == null) LocalYujiColors.current.TextMuted else LocalTrend.current.of(value.signum())
    CnyText(value, style, modifier, color = color, signed = true)
}

@Composable
fun DeltaChip(label: String, value: BigDecimal?, modifier: Modifier = Modifier) {
    val c = LocalYujiColors.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(c.Muted)
            .border(1.dp, c.Outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.TextMuted)
        Spacer(Modifier.height(2.dp))
        if (value == null) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = c.TextFaint)
        } else {
            DeltaText(value, MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
fun AccountIcon(iconType: String, iconValue: String, name: String, size: Dp = 40.dp) {
    val c = LocalYujiColors.current
    val shape = RoundedCornerShape(size * 0.3f)
    when (iconType) {
        IconType.IMAGE -> {
            val context = LocalContext.current
            val filename = iconValue.substringAfterLast("/")
            val isIconify = filename.startsWith("iconify_")
            // Prefer the stored path; fall back to icons/ dir if the file was moved (e.g. after restore).
            val file = remember(iconValue) {
                val f = File(iconValue)
                if (f.exists()) f else File(context.filesDir, "icons/$filename")
            }
            val painter = rememberAsyncImagePainter(model = file)
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Dark plate only for Iconify icons (white SVGs). Album photos use no background.
                modifier = Modifier.size(size).clip(shape)
                    .let { m -> if (isIconify) m.background(c.InverseSurface).border(1.dp, c.Outline, shape) else m },
            )
        }
        IconType.EMOJI -> Box(
            Modifier.size(size).clip(shape).background(c.Muted).border(1.dp, c.Outline, shape),
            contentAlignment = Alignment.Center,
        ) { Text(iconValue, fontSize = (size.value * 0.5f).sp) }
        else -> {
            val color = c.Palette[(name.hashCode() and 0x7fffffff) % c.Palette.size]
            Box(
                Modifier.size(size).clip(shape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.22f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().take(1).ifEmpty { "¥" },
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YujiTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}

/** Rounded icon plate in the signature blue gradient with a white icon. */
@Composable
fun IconPlate(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 32.dp, iconSize: Dp = 18.dp) {
    val c = LocalYujiColors.current
    Box(
        modifier.size(size).clip(MaterialTheme.shapes.medium).background(Brush.linearGradient(listOf(c.Accent, c.AccentBright))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconPlate(icon, size = 56.dp, iconSize = 26.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(action.first, onClick = action.second, fillWidth = false)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (destructive) LocalYujiColors.current.Coral else LocalYujiColors.current.AccentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
        containerColor = LocalYujiColors.current.CardHigh,
    )
}

/**
 * Primary button in the signature gradient, lifted on an accent-tinted shadow (Android 9+
 * colors the shadow; older versions show a neutral one). Presses to 98%.
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fillWidth: Boolean = true) {
    val c = LocalYujiColors.current
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(Motion.QUICK, easing = Motion.ExpoOut), label = "btn-scale")
    val glow by animateDpAsState(if (!enabled) 0.dp else if (pressed) 6.dp else 14.dp, tween(Motion.QUICK, easing = Motion.ExpoOut), label = "btn-glow")
    val fill = if (enabled) Brush.horizontalGradient(listOf(c.Accent, c.AccentBright)) else SolidColor(c.Muted)
    Box(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(glow, shape, clip = false, ambientColor = c.Accent, spotColor = c.Accent)
            .clip(shape)
            .background(fill)
            .then(if (enabled) Modifier else Modifier.border(1.dp, c.Outline, shape))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else c.TextFaint)
    }
}

/** Outline button for secondary actions: transparent, hairline border, tints on press. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = LocalYujiColors.current
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(Motion.QUICK, easing = Motion.ExpoOut), label = "btn2-scale")
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (pressed) c.MutedPressed else Color.Transparent)
            .border(1.dp, if (pressed) c.AccentBorder else c.OutlineStrong, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) c.AccentText else c.TextFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = if (enabled) c.Text else c.TextFaint)
    }
}

/** Pill filter/choice chip: accent-tinted when selected, glass otherwise. */
@Composable
fun YujiChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null) {
    val c = LocalYujiColors.current
    val bg by animateColorAsState(if (selected) c.AccentSoft else c.Muted, tween(Motion.QUICK), label = "chip-bg")
    val border by animateColorAsState(if (selected) c.AccentBorder else c.Outline, tween(Motion.QUICK), label = "chip-border")
    val fg by animateColorAsState(if (selected) c.AccentText else c.TextMuted, tween(Motion.QUICK), label = "chip-fg")
    Row(
        modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
    }
}

@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: String? = null, pulse: Boolean = false) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pulse) PulsingDot(color) else Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.Text, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun KeyValueRow(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted)
        value()
    }
}

@Composable
fun yujiSwitchColors(): SwitchColors {
    val c = LocalYujiColors.current
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = c.Accent,
        checkedBorderColor = c.Accent,
        uncheckedThumbColor = c.TextMuted,
        uncheckedTrackColor = c.Muted,
        uncheckedBorderColor = c.OutlineStrong,
    )
}

/**
 * Hairline border for one slice of a card that is split across list items (group header +
 * account rows). Sides always; top/bottom edges with rounded corners only on the ends.
 */
fun Modifier.segmentBorder(top: Boolean, bottom: Boolean, radius: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val r = radius.toPx()
    val w = size.width
    val h = size.height
    val half = 0.5f
    val stroke = Stroke(1f)
    val y0 = if (top) r else 0f
    val y1 = if (bottom) h - r else h
    drawLine(color, Offset(half, y0), Offset(half, y1), 1f)
    drawLine(color, Offset(w - half, y0), Offset(w - half, y1), 1f)
    if (top) {
        drawLine(color, Offset(r, half), Offset(w - r, half), 1f)
        drawArc(color, 180f, 90f, false, Offset(half, half), Size(2 * r, 2 * r), style = stroke)
        drawArc(color, 270f, 90f, false, Offset(w - 2 * r - half, half), Size(2 * r, 2 * r), style = stroke)
    }
    if (bottom) {
        drawLine(color, Offset(r, h - half), Offset(w - r, h - half), 1f)
        drawArc(color, 90f, 90f, false, Offset(half, h - 2 * r - half), Size(2 * r, 2 * r), style = stroke)
        drawArc(color, 0f, 90f, false, Offset(w - 2 * r - half, h - 2 * r - half), Size(2 * r, 2 * r), style = stroke)
    }
}

/** Soft accent light under a solid accent element (colored on Android 9+). */
@Composable
fun Modifier.accentGlow(shape: Shape, elevation: Dp = 12.dp): Modifier {
    val accent = LocalYujiColors.current.Accent
    return shadow(elevation, shape, clip = false, ambientColor = accent, spotColor = accent)
}

/** Input-like surface (value fields, pickers, tiles): an opaque well with a visible border. */
@Composable
fun Modifier.inputWell(shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    val c = LocalYujiColors.current
    return clip(shape).background(c.Card).border(1.dp, c.OutlineStrong, shape)
}

/** Fixed bottom action area: chrome color, nearly opaque, separated by a hairline. */
@Composable
fun Modifier.bottomBarSurface(): Modifier {
    val c = LocalYujiColors.current
    return background(c.Chrome.copy(alpha = 0.94f))
        .drawWithContent {
            drawContent()
            drawLine(c.Outline, Offset(0f, 0f), Offset(size.width, 0f), 1f)
        }
}


/** Attention dot that breathes: 2s scale/opacity pulse with a fading halo. */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "pulse-t",
    )
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).graphicsLayer {
                scaleX = 1f + t * 1.4f; scaleY = 1f + t * 1.4f; alpha = 0.45f * (1f - t)
            }.clip(CircleShape).background(color),
        )
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}
