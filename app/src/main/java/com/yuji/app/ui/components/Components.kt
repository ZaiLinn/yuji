package com.yuji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil3.compose.rememberAsyncImagePainter
import com.yuji.app.data.db.IconType
import com.yuji.app.domain.Money
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.LocalTrend
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import java.io.File
import java.math.BigDecimal

const val MASK = "••••••"

@Composable
fun YujiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.Unspecified,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalYujiColors.current.Card else color
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = resolvedColor,
    ) {
        Column(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

/** CNY amount that respects the global "hide amounts" switch. */
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
    val resolvedColor = if (color == Color.Unspecified) LocalYujiColors.current.Text else color
    val text = when {
        value == null -> "—"
        LocalHideAmounts.current -> MASK
        signed -> Money.signedCny(value)
        compact -> Money.compactCny(value)
        else -> Money.cny(value)
    }
    Text(text, style = style, color = resolvedColor, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
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
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LocalYujiColors.current.CardHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalYujiColors.current.TextMuted)
        Spacer(Modifier.height(2.dp))
        if (value == null) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = LocalYujiColors.current.TextFaint)
        } else {
            DeltaText(value, MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
fun AccountIcon(iconType: String, iconValue: String, name: String, size: Dp = 40.dp) {
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
                // Dark background only for Iconify icons (white SVGs). Album photos use no background.
                modifier = Modifier.size(size).clip(shape)
                    .let { m -> if (isIconify) m.background(Color(0xFF1D2126)) else m },
            )
        }
        IconType.EMOJI -> Box(
            Modifier.size(size).clip(shape).background(LocalYujiColors.current.CardHigh),
            contentAlignment = Alignment.Center,
        ) { Text(iconValue, fontSize = (size.value * 0.5f).sp) }
        else -> {
            val color = YujiColors.Palette[(name.hashCode() and 0x7fffffff) % YujiColors.Palette.size]
            Box(
                Modifier.size(size).clip(shape).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().take(1).ifEmpty { "¥" },
                    color = color,
                    fontWeight = FontWeight.Bold,
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = LocalYujiColors.current.Background, scrolledContainerColor = LocalYujiColors.current.Background),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(LocalYujiColors.current.Card), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LocalYujiColors.current.Mint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalYujiColors.current.TextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = action.second) { Text(action.first) }
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
                Text(confirm, color = if (destructive) LocalYujiColors.current.Coral else LocalYujiColors.current.Mint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = LocalYujiColors.current.TextMuted) } },
        containerColor = LocalYujiColors.current.CardHigh,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LocalYujiColors.current.Mint, contentColor = Color(0xFF00210F)),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
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
