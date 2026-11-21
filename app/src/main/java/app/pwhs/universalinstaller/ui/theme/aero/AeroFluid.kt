package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (PI * 2.0).toFloat()

/**
 * Recessed glass trough with a vivid liquid fill, a moving meniscus and a glowing leading bead.
 * Smoothly animates whenever [progress] changes, and rises/falls with micro-bubbles inside.
 */
@Composable
fun AeroLiquidProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    showBubbles: Boolean = true,
    colors: List<Color>? = null,
) {
    val tokens = LocalAeroTokens.current ?: return
    val motion = LocalAeroMotion.current
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "aero-liquid-fill",
    )
    val transition = rememberInfiniteTransition(label = "aero-liquid")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "aero-liquid-shimmer",
    )
    val liquid = colors ?: listOf(tokens.liquidStart, tokens.liquidMid, tokens.liquidEnd)
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .aeroRecessedTrough(tokens, shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillWidth = size.width * animated
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            if (fillWidth > 1f) {
                // Liquid body.
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = liquid,
                        startX = 0f,
                        endX = size.width,
                    ),
                    size = Size(fillWidth.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
                // Curved top gloss running the full length of the fill.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startY = 1f,
                        endY = size.height * 0.62f,
                    ),
                    topLeft = Offset(1f, 1f),
                    size = Size(
                        (fillWidth - 2f).coerceAtLeast(1f),
                        size.height * 0.5f,
                    ),
                    cornerRadius = radius,
                )
                // Diagonal caustic sheen sweeping across the liquid while it moves.
                if (motion) {
                    val sheenX = -size.width * 0.4f + (size.width * 1.8f) * shimmer
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                tokens.liquidShimmer.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                            startX = sheenX,
                            endX = sheenX + size.width * 0.35f,
                        ),
                        size = Size(fillWidth.coerceAtLeast(1f), size.height),
                    )
                }
                // Glowing droplet cap at the leading edge.
                val beadX = (fillWidth - size.height / 2f).coerceAtLeast(size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.95f), liquid.last().copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(beadX, size.height / 2f),
                        radius = size.height * 0.55f,
                    ),
                    radius = size.height * 0.55f,
                    center = Offset(beadX, size.height / 2f),
                )
                if (showBubbles && motion) {
                    for (i in 0 until 3) {
                        val phase = (shimmer + i * 0.33f) % 1f
                        val bx = size.height * 0.8f + (fillWidth - size.height) * ((i * 0.37f) % 1f)
                        val by = size.height * (0.85f - phase * 0.7f)
                        val br = size.height * (0.10f + 0.05f * i)
                        if (bx < fillWidth) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.45f * (1f - phase)),
                                radius = br,
                                center = Offset(bx, by),
                            )
                        }
                    }
                }
            }
            // Rim + top inner shadow stay above the liquid so the trough always reads as glass.
            drawRoundRect(
                color = tokens.hairline,
                cornerRadius = radius,
                style = Stroke(width = 1f),
            )
        }
    }
}

/**
 * Vista-style frosted glass tank whose liquid level follows storage usage. <75% aqua/emerald,
 * 75–90% sunlit amber with a gentle pulse, >90% glowing ruby coral with a warning aura.
 */
@Composable
fun AeroStorageTank(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val tokens = LocalAeroTokens.current ?: return
    val motion = LocalAeroMotion.current
    val level = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = level,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "aero-tank-level",
    )
    val transition = rememberInfiniteTransition(label = "aero-tank")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "aero-tank-wave",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "aero-tank-pulse",
    )
    val liquid = when {
        level >= 0.9f -> listOf(Color(0xFFFF8FA3), Color(0xFFF0446B), Color(0xFFC41340))
        level >= 0.75f -> listOf(Color(0xFFFFD27A), Color(0xFFFFB020), Color(0xFFE08A00))
        else -> listOf(tokens.liquidStart, tokens.liquidMid, tokens.liquidEnd)
    }
    val warningAura = when {
        level >= 0.9f -> tokens.dangerRuby.copy(alpha = 0.25f * (if (motion) pulse else 0.6f))
        level >= 0.75f -> tokens.warningAmber.copy(alpha = 0.18f * (if (motion) pulse else 0.6f))
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(20.dp)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = CornerRadius(size.height * 0.28f, size.height * 0.28f)
            // Glass shell.
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = warningAura,
                cornerRadius = radius,
                style = Stroke(width = 5f),
            )
            // Liquid.
            val fillTop = size.height * (1f - animated)
            val waveAmp = (size.height * 0.06f) * (if (motion) 1f else 0f)
            val step = size.width / 24f
            var prev = Offset(0f, fillTop + sin(wave * TAU) * waveAmp)
            for (i in 1..24) {
                val x = step * i
                val y = fillTop + sin(wave * TAU + i * 0.55f) * waveAmp
                drawLine(color = liquid[liquid.size / 2], start = prev, end = Offset(x, y), strokeWidth = 2f)
                prev = Offset(x, y)
            }
            if (animated > 0.01f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = liquid,
                        startY = fillTop,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, fillTop),
                    size = Size(size.width, size.height - fillTop),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(tokens.glossTop.copy(alpha = 0.35f), Color.Transparent),
                        startY = fillTop,
                        endY = fillTop + (size.height - fillTop) * 0.5f,
                    ),
                    topLeft = Offset(2f, fillTop + 1f),
                    size = Size(size.width - 4f, (size.height - fillTop) * 0.45f),
                    cornerRadius = radius,
                )
            }
            // Inner top shadow + outer hairline.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.4f,
                ),
                cornerRadius = radius,
                style = Stroke(width = 2f),
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(tokens.rimTop, tokens.rimMid, tokens.rimBottom)),
                cornerRadius = radius,
                style = Stroke(width = 1.dp.toPx()),
            )
            // Meniscus catchlight along the liquid surface.
            if (animated in 0.02f..0.98f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(4f, fillTop),
                    end = Offset(size.width - 4f, fillTop),
                    strokeWidth = 1.4f,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
            content = content,
        )
    }
}
