package com.yuji.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Page background, cached per size (radial gradients stand in for blurred glows, so it works on
 * every API level — Modifier.blur needs Android 12).
 * - Light: warm off-white with faint electric-blue glows tucked into the corners.
 * - Dark: deep slate with a dot texture and the same corner glows, a little stronger.
 */
@Composable
fun Modifier.ambientBackground(): Modifier {
    val c = LocalYujiColors.current
    return drawWithCache {
        val w = size.width
        val h = size.height
        val strength = if (c.isLight) 1f else 1.8f
        val glows = listOf(
            glow(c.Accent, 0.07f * strength, Offset(w * 1.05f, -h * 0.02f), w * 0.9f),
            glow(c.AccentBright, 0.05f * strength, Offset(-w * 0.15f, h * 0.55f), w * 0.8f),
            glow(c.Accent, 0.04f * strength, Offset(w * 0.9f, h * 1.02f), w * 0.7f),
        )
        val dots = if (c.isLight) null else dotBrush(20.dp.toPx(), 1.dp.toPx(), Color.White.copy(alpha = 0.05f))
        onDrawBehind {
            drawRect(c.Background)
            glows.forEach { drawRect(it) }
            dots?.let { drawRect(it) }
        }
    }
}

private fun glow(color: Color, alpha: Float, center: Offset, radius: Float) = Brush.radialGradient(
    0f to color.copy(alpha = alpha), 0.45f to color.copy(alpha = alpha * 0.45f), 1f to Color.Transparent,
    center = center, radius = radius,
)

/** A repeating one-dot tile, so a full-screen dot grid costs a single rect draw. */
private fun dotBrush(spacing: Float, radius: Float, color: Color): Brush {
    val n = spacing.roundToInt().coerceAtLeast(2)
    val bitmap = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    android.graphics.Canvas(bitmap).drawCircle(n / 2f, n / 2f, radius, paint)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * App-wide press feedback replacing the ripple: a soft accent spotlight (300dp across) that
 * lights up under the finger, plus a faint surface lift. A quick tap still reaches full glow
 * before fading, so the feedback is never lost.
 */
data class SpotlightIndication(val color: Color, val lift: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SpotlightNode(interactionSource, color, lift)
}

private class SpotlightNode(
    private val source: InteractionSource,
    private val color: Color,
    private val lift: Color,
) : Modifier.Node(), DrawModifierNode {
    private val glow = Animatable(0f)
    private var center = Offset.Unspecified
    private var pressJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        center = interaction.pressPosition
                        pressJob = launch { glow.animateTo(1f, tween(Motion.QUICK, easing = Motion.ExpoOut)) { invalidateDraw() } }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val press = pressJob
                        launch {
                            press?.join()
                            glow.animateTo(0f, tween(Motion.STANDARD, easing = Motion.ExpoOut)) { invalidateDraw() }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = glow.value
        if (a <= 0f || !center.isSpecified) return
        val radius = 150.dp.toPx()
        clipRect {
            drawRect(lift.copy(alpha = lift.alpha * a))
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.18f * a), Color.Transparent), center, radius),
                radius = radius,
                center = center,
            )
        }
    }
}
