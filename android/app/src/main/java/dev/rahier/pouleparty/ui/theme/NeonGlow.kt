package dev.rahier.pouleparty.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Intensity of the neon glow halo, mirroring the iOS `NeonGlow.GlowIntensity`.
 * Higher intensities stack more blurred layers at wider radii for a stronger
 * "bright lamp" feel — used on key CTAs (countdown text, active power-up badges).
 * `SUBTLE` is best for incidental accents (map bar buttons, notifications).
 */
enum class NeonGlowIntensity { SUBTLE, MEDIUM, INTENSE }

/**
 * Draws a neon-style blurred halo behind the composable's bounds, matching
 * the iOS `.neonGlow(_:intensity:)` modifier.
 *
 * Uses `BlurMaskFilter` so the blur extends past the layout bounds (unlike
 * `Modifier.shadow`, which produces an elevation-only drop shadow).
 * Apply before `.background(...)` so the halo sits underneath the content.
 *
 * @param color The glow hue. Usually matches the brand / power-up / zone state color.
 * @param intensity Halo strength: SUBTLE (single layer, 4dp), MEDIUM (two layers,
 *   7+14dp), INTENSE (three layers, 7+14+28dp).
 * @param cornerRadius Shape of the halo. Use `0.dp` for rectangular components,
 *   pass the circle's diameter for round FABs/badges, or any intermediate value
 *   for rounded-corner cards.
 */
fun Modifier.neonGlow(
    color: Color,
    intensity: NeonGlowIntensity = NeonGlowIntensity.MEDIUM,
    cornerRadius: Dp = 0.dp
): Modifier = this
    // Force an offscreen compositing layer so `BlurMaskFilter` renders on all
    // API levels (it's a no-op on hardware-accelerated canvases before API 28).
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawBehind {
        val layers = when (intensity) {
            NeonGlowIntensity.SUBTLE -> listOf(4.dp to 0.4f)
            NeonGlowIntensity.MEDIUM -> listOf(7.dp to 0.6f, 14.dp to 0.3f)
            NeonGlowIntensity.INTENSE -> listOf(
                7.dp to 0.8f,
                14.dp to 0.5f,
                28.dp to 0.3f
            )
        }
        val cornerPx = cornerRadius.toPx()
        layers.forEach { (radiusDp, alpha) ->
            val radiusPx = radiusDp.toPx()
            val paint = Paint().apply {
                this.color = color.copy(alpha = alpha).toArgb()
                maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawRoundRect(
                0f, 0f, size.width, size.height,
                cornerPx, cornerPx,
                paint
            )
        }
    }
