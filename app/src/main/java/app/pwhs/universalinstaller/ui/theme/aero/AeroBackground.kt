package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.AeroDecorationLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (PI * 2.0).toFloat()

/**
 * The living Aero backdrop: atmospheric sky wash, sweeping aurora ribbons, soft sun blooms and
 * rising iridescent micro-bubbles. Everything is procedural and allocated up front; the only
 * per-frame work is arithmetic and draws.
 */
@Composable
fun AeroBackgroundCanvas(
    tokens: AeroTokens,
    decoration: AeroDecorationLevel,
    animated: Boolean,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = LocalAeroBlur.current,
    showCaustics: Boolean = false,
) {
    var clock by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(animated, decoration) {
        if (!animated || decoration == AeroDecorationLevel.MINIMAL) return@LaunchedEffect
        // Pauses automatically whenever the screen drops below STARTED.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var startedAt = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (startedAt == 0L) startedAt = now
                    clock = ((now - startedAt) / 1_000_000_000f) % 3600f
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Atmospheric wash — always present, even in MINIMAL/reduced motion.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tokens.sky.skyTop,
                        tokens.sky.skyMid,
                        tokens.sky.skyHorizon,
                        tokens.sky.skyBottom,
                    ),
                ),
            )
            drawBloomLayer(tokens, clock, decoration)
        }

        // Ribbons get a real frosted blur on API 31+; older devices keep the soft gradients.
        val ribbonModifier = if (blurEnabled) {
            Modifier
                .fillMaxSize()
                .blur(26.dp, BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier.fillMaxSize()
        }
        Canvas(modifier = ribbonModifier) {
            drawAuroraRibbons(tokens, clock, decoration)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBubbles(tokens, clock, decoration)
            if (showCaustics) drawCaustics(tokens, clock)
        }
    }
}

private fun DrawScope.drawBloomLayer(
    tokens: AeroTokens,
    clock: Float,
    decoration: AeroDecorationLevel,
) {
    val count = when (decoration) {
        AeroDecorationLevel.MINIMAL -> 2
        AeroDecorationLevel.BALANCED -> 4
        AeroDecorationLevel.FULL -> 6
    }
    for (i in 0 until count) {
        val fx = 0.12f + (i * 0.171f) % 0.8f
        val fy = 0.08f + (i * 0.137f) % 0.55f
        val breathe = if (clock == 0f) 0.55f else 0.5f + 0.5f * sin(clock * 0.21f + i * 1.7f)
        val radius = size.minDimension * (0.20f + 0.05f * (i % 3))
        val tint = when (i % 3) {
            0 -> tokens.sky.sunBloom
            1 -> tokens.sky.auroraPrimary
            else -> tokens.sky.auroraHighlight
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.34f + 0.12f * breathe), Color.Transparent),
                center = Offset(size.width * fx, size.height * fy),
                radius = radius * 1.6f,
            ),
            radius = radius * 1.6f,
            center = Offset(size.width * fx, size.height * fy),
        )
    }
}

private fun DrawScope.drawAuroraRibbons(
    tokens: AeroTokens,
    clock: Float,
    decoration: AeroDecorationLevel,
) {
    if (decoration == AeroDecorationLevel.MINIMAL) return
    val w = size.width
    val h = size.height
    val phase = clock * 0.16f
    for (index in 0 until 3) {
        val baseY = h * (0.10f + index * 0.085f)
        val amplitude = h * (0.055f + index * 0.008f)
        val path = Path()
        path.moveTo(-w * 0.15f, baseY + sin(phase + index) * amplitude)
        path.cubicTo(
            w * 0.18f, baseY - amplitude * (1.4f + sin(phase * 1.3f + index) * 0.5f),
            w * 0.46f, baseY + amplitude * (1.8f + cos(phase + index) * 0.4f),
            w * 0.72f, baseY + amplitude * 0.4f,
        )
        path.cubicTo(
            w * 0.88f, baseY - amplitude * 0.9f,
            w * 1.02f, baseY + amplitude * 0.8f,
            w * 1.15f, baseY + sin(phase + index * 2f) * amplitude,
        )
        val color = when (index) {
            0 -> tokens.sky.auroraPrimary
            1 -> tokens.sky.auroraSecondary
            else -> tokens.sky.auroraHighlight
        }
        val alpha = (0.28f - index * 0.05f).coerceAtLeast(0.12f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.7f),
                    Color.Transparent,
                ),
                start = Offset(0f, baseY - amplitude),
                end = Offset(w, baseY + amplitude * 2f),
            ),
            style = Stroke(width = h * (0.075f - index * 0.012f), cap = StrokeCap.Round),
        )
        // Glowing edge along the top of each ribbon.
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    tokens.sky.auroraHighlight.copy(alpha = alpha * 0.9f),
                    Color.Transparent,
                ),
            ),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawBubbles(
    tokens: AeroTokens,
    clock: Float,
    decoration: AeroDecorationLevel,
) {
    val count = when (decoration) {
        AeroDecorationLevel.MINIMAL -> 0
        AeroDecorationLevel.BALANCED -> 8
        AeroDecorationLevel.FULL -> 16
    }
    if (count == 0) return

    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val seed = ((i * 37) % 100) / 100f
        val depth = 0.35f + ((i * 53) % 65) / 100f
        val radius = (3f + depth * 14f) * (w / 400f).coerceAtLeast(0.7f)
        val speed = 0.012f + depth * 0.02f
        val progress = ((clock * speed) + seed) % 1f
        val centerX = w * (0.06f + seed * 0.9f) + sin(progress * TWO_PI + i * 1.9f) * (10f + depth * 18f)
        val centerY = h + radius * 2f - progress * (h + radius * 4f)
        val edgeFade = minOf(progress / 0.07f, (1f - progress) / 0.12f).coerceIn(0f, 1f)
        if (edgeFade <= 0.01f) continue

        drawCircle(
            color = tokens.bubbleFill.copy(alpha = tokens.bubbleFill.alpha * edgeFade),
            radius = radius,
            center = Offset(centerX, centerY),
        )
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    tokens.sky.auroraPrimary.copy(alpha = 0.45f * edgeFade),
                    Color.White.copy(alpha = 0.55f * edgeFade),
                    tokens.sky.auroraSecondary.copy(alpha = 0.40f * edgeFade),
                    tokens.bubbleRim.copy(alpha = 0.35f * edgeFade),
                    tokens.sky.auroraPrimary.copy(alpha = 0.45f * edgeFade),
                ),
                center = Offset(centerX, centerY),
            ),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = (1.2f + depth).coerceAtMost(3f)),
        )
        // Specular crescent top-left + tiny bounce dot bottom-right.
        drawCircle(
            color = tokens.bubbleSpecular.copy(alpha = 0.85f * edgeFade),
            radius = radius * 0.26f,
            center = Offset(centerX - radius * 0.38f, centerY - radius * 0.42f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.45f * edgeFade),
            radius = radius * 0.14f,
            center = Offset(centerX + radius * 0.40f, centerY + radius * 0.44f),
        )
    }
}

private fun DrawScope.drawCaustics(tokens: AeroTokens, clock: Float) {
    val w = size.width
    val h = size.height
    val bands = 3
    for (b in 0 until bands) {
        val path = Path()
        path.moveTo(0f, h * (0.18f + b * 0.06f))
        val step = w / 4f
        for (s in 0 until 4) {
            val x0 = step * s
            val x1 = step * (s + 1)
            path.cubicTo(
                x0 + step * 0.3f, h * (0.18f + b * 0.06f) + sin(clock * 0.6f + s + b) * h * 0.02f,
                x0 + step * 0.7f, h * (0.18f + b * 0.06f) - sin(clock * 0.5f + s * 1.3f + b) * h * 0.02f,
                x1, h * (0.18f + b * 0.06f),
            )
        }
        drawPath(
            path = path,
            color = tokens.caustic.copy(alpha = 0.10f + b * 0.015f),
            style = Stroke(width = 2.5f + b),
        )
    }
}

/** Fills [modifier] with the Aero sky, without any motion. Used by cards and pickers. */
@Composable
fun AeroSkySwatch(
    tokens: AeroTokens,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(
                    tokens.sky.skyTop,
                    tokens.sky.skyMid,
                    tokens.sky.skyHorizon,
                    tokens.sky.skyBottom,
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
    }
}

/** Static miniature of the animated scene: sky, ribbon, one bubble — for the settings preview. */
@Composable
fun AeroMiniScene(
    tokens: AeroTokens,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "aero-mini")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aero-mini-drift",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(tokens.sky.skyTop, tokens.sky.skyMid, tokens.sky.skyHorizon),
                startY = 0f,
                endY = h,
            ),
        )
        val path = Path()
        path.moveTo(-w * 0.1f, h * 0.32f)
        path.cubicTo(
            w * 0.25f, h * 0.16f + sin(drift * TWO_PI) * h * 0.05f,
            w * 0.62f, h * 0.50f,
            w * 1.15f, h * 0.28f,
        )
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    tokens.sky.auroraPrimary.copy(alpha = 0.45f),
                    tokens.sky.auroraSecondary.copy(alpha = 0.35f),
                    Color.Transparent,
                ),
            ),
            style = Stroke(width = h * 0.16f, cap = StrokeCap.Round),
        )
        val bubbleRadius = w * 0.055f
        val bubbleY = h * (0.86f - 0.5f * drift * 0.3f)
        drawCircle(
            color = tokens.bubbleFill,
            radius = bubbleRadius,
            center = Offset(w * 0.24f, bubbleY),
        )
        drawCircle(
            color = tokens.bubbleRim,
            radius = bubbleRadius,
            center = Offset(w * 0.24f, bubbleY),
            style = Stroke(width = 1.5f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = bubbleRadius * 0.28f,
            center = Offset(w * 0.24f - bubbleRadius * 0.32f, bubbleY - bubbleRadius * 0.36f),
        )
    }
}
