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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (PI * 2.0).toFloat()

/** Which procedural illustration an empty state shows. */
enum class AeroIllustration { PRISM, DROP_RADAR, GLASS_ORB }

/**
 * The package icon resting on an elliptical gloss pad with a radial halo and a faint mirrored
 * reflection beneath — used by the preview sheet and app details.
 */
@Composable
fun AeroIconPedestal(
    modifier: Modifier = Modifier,
    haloColor: Color = LocalAeroTokens.current?.activeCyan ?: Color(0xFF00C8FF),
    size: Dp = 112.dp,
    icon: @Composable () -> Unit,
) {
    val tokens = LocalAeroTokens.current ?: return
    Box(modifier = modifier.size(size * 1.3f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height * 0.44f
            val haloR = this.size.minDimension * 0.52f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(haloColor.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = haloR,
                ),
                radius = haloR,
                center = Offset(cx, cy),
            )
            // Glass pad with a bright front rim.
            val padW = this.size.width * 0.74f
            val padH = this.size.height * 0.14f
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(tokens.glassTop, tokens.glassBottom),
                    startY = cy + this.size.height * 0.22f,
                    endY = cy + this.size.height * 0.22f + padH,
                ),
                topLeft = Offset(cx - padW / 2f, cy + this.size.height * 0.22f),
                size = Size(padW, padH),
            )
            drawOval(
                color = tokens.meniscus.copy(alpha = 0.8f),
                topLeft = Offset(cx - padW / 2f, cy + this.size.height * 0.22f),
                size = Size(padW, padH),
                style = Stroke(width = 1.4f),
            )
            // Mirror fade under the pad.
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(haloColor.copy(alpha = 0.20f), Color.Transparent),
                    startY = cy + this.size.height * 0.36f,
                    endY = cy + this.size.height * 0.48f,
                ),
                topLeft = Offset(cx - padW * 0.35f, cy + this.size.height * 0.36f),
                size = Size(padW * 0.7f, this.size.height * 0.12f),
            )
        }
        icon()
    }
}

/** Expanding water-ripple rings, used while a device scan runs. */
@Composable
fun AeroSonarRings(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    color: Color = LocalAeroTokens.current?.activeCyan ?: Color(0xFF00C8FF),
    size: Dp = 160.dp,
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "aero-sonar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "aero-sonar-phase",
    )
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f
        for (ring in 0 until 3) {
            val p = (phase + ring * 0.33f) % 1f
            val r = maxR * (0.2f + p * 0.8f)
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.45f),
                radius = r,
                center = center,
                style = Stroke(width = 2f),
            )
        }
    }
}

/** Concentric aqua-emerald ripple that radiates outward on a successful install. */
@Composable
fun AeroSuccessRipple(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    size: Dp = 160.dp,
) {
    val tokens = LocalAeroTokens.current ?: return
    if (!visible) return
    val transition = rememberInfiniteTransition(label = "aero-success")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "aero-success-phase",
    )
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f
        for (ring in 0 until 3) {
            val p = (phase + ring * 0.22f) % 1f
            val r = maxR * (0.25f + p * 0.75f)
            drawCircle(
                color = tokens.safeEmerald.copy(alpha = (1f - p) * 0.40f),
                radius = r,
                center = center,
                style = Stroke(width = 2.5f),
            )
            drawCircle(
                color = tokens.activeCyan.copy(alpha = (1f - p) * 0.25f),
                radius = r * 0.86f,
                center = center,
                style = Stroke(width = 1.2f),
            )
        }
    }
}

/** Luminous corner brackets around a QR pairing card. */
fun Modifier.aeroCornerBrackets(
    color: Color,
    cornerLength: Dp = 22.dp,
    stroke: Dp = 2.dp,
): Modifier = this.drawBehind {
    val len = cornerLength.toPx()
    val w = stroke.toPx()
    val inset = w / 2f
    val points = listOf(
        // top-left
        Offset(inset, inset) to Offset(inset + len, inset),
        Offset(inset, inset) to Offset(inset, inset + len),
        // top-right
        Offset(size.width - inset, inset) to Offset(size.width - inset - len, inset),
        Offset(size.width - inset, inset) to Offset(size.width - inset, inset + len),
        // bottom-left
        Offset(inset, size.height - inset) to Offset(inset + len, size.height - inset),
        Offset(inset, size.height - inset) to Offset(inset, size.height - inset - len),
        // bottom-right
        Offset(size.width - inset, size.height - inset) to Offset(size.width - inset - len, size.height - inset),
        Offset(size.width - inset, size.height - inset) to Offset(size.width - inset, size.height - inset - len),
    )
    points.forEach { (start, end) ->
        drawLine(color = color, start = start, end = end, strokeWidth = w, cap = StrokeCap.Round)
    }
}

/**
 * Glass bridge with data droplets drifting between two endpoints, shown during active
 * LAN / Send-to-TV transfers.
 */
@Composable
fun AeroTransferBridge(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val tokens = LocalAeroTokens.current ?: return
    val transition = rememberInfiniteTransition(label = "aero-transfer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "aero-transfer-phase",
    )
    Canvas(modifier = modifier) {
        val y = this.size.height / 2f
        val path = Path()
        path.moveTo(10f, y)
        path.cubicTo(
            this.size.width * 0.3f, y - this.size.height * 0.35f,
            this.size.width * 0.7f, y + this.size.height * 0.35f,
            this.size.width - 10f, y,
        )
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    tokens.activeCyan.copy(alpha = 0.55f),
                    tokens.safeEmerald.copy(alpha = 0.55f),
                    Color.Transparent,
                ),
            ),
            style = Stroke(width = this.size.height * 0.18f, cap = StrokeCap.Round),
        )
        if (active) {
            for (i in 0 until 3) {
                val p = (phase + i * 0.33f) % 1f
                val x = 10f + (this.size.width - 20f) * p
                val wobble = sin(p * TAU + i) * this.size.height * 0.22f
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f * (1f - p * 0.4f)),
                    radius = this.size.height * 0.10f,
                    center = Offset(x, y + wobble),
                )
                drawCircle(
                    color = tokens.activeCyan.copy(alpha = 0.5f),
                    radius = this.size.height * 0.17f,
                    center = Offset(x, y + wobble),
                    style = Stroke(width = 1.2f),
                )
            }
        }
    }
}

/** Procedural empty-state art: crystal prism, water-drop radar, or glass orb with an eco leaf. */
@Composable
fun AeroEmptyIllustration(
    kind: AeroIllustration,
    modifier: Modifier = Modifier,
    accent: Color = LocalAeroTokens.current?.activeCyan ?: Color(0xFF00C8FF),
) {
    val tokens = LocalAeroTokens.current ?: return
    val transition = rememberInfiniteTransition(label = "aero-empty")
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "aero-empty-float",
    )
    Canvas(modifier = modifier.size(150.dp)) {
        val cx = this.size.width / 2f
        val cy = this.size.height * 0.48f + sin(float * TAU) * this.size.height * 0.03f
        val r = this.size.minDimension * 0.30f
        // Shared halo.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 2.4f,
            ),
            radius = r * 2.4f,
            center = Offset(cx, cy),
        )
        when (kind) {
            AeroIllustration.PRISM -> {
                val path = Path().apply {
                    moveTo(cx, cy - r * 1.25f)
                    lineTo(cx + r * 0.95f, cy + r * 0.65f)
                    lineTo(cx - r * 0.95f, cy + r * 0.65f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            tokens.sky.auroraPrimary.copy(alpha = 0.65f),
                            tokens.sky.auroraSecondary.copy(alpha = 0.55f),
                        ),
                        start = Offset(cx, cy - r),
                        end = Offset(cx + r, cy + r),
                    ),
                )
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        listOf(Color.White, tokens.rimMid, tokens.rimBottom),
                    ),
                    style = Stroke(width = 2f),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(cx, cy - r * 1.25f),
                    end = Offset(cx, cy + r * 0.65f),
                    strokeWidth = 1.2f,
                )
            }

            AeroIllustration.DROP_RADAR -> {
                for (ring in 0 until 3) {
                    val p = (float + ring * 0.3f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * 0.5f),
                        radius = r * (0.5f + p * 1.4f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.6f),
                    )
                }
                // Water droplet.
                val drop = Path().apply {
                    moveTo(cx, cy - r * 0.95f)
                    cubicTo(
                        cx + r * 0.75f, cy - r * 0.2f,
                        cx + r * 0.62f, cy + r * 0.7f,
                        cx, cy + r * 0.7f,
                    )
                    cubicTo(
                        cx - r * 0.62f, cy + r * 0.7f,
                        cx - r * 0.75f, cy - r * 0.2f,
                        cx, cy - r * 0.95f,
                    )
                    close()
                }
                drawPath(
                    path = drop,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.9f),
                            tokens.sky.auroraPrimary,
                            tokens.sky.auroraSecondary,
                        ),
                        startY = cy - r,
                        endY = cy + r,
                    ),
                )
                drawPath(drop, color = Color.White.copy(alpha = 0.75f), style = Stroke(width = 1.6f))
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = r * 0.14f,
                    center = Offset(cx - r * 0.22f, cy - r * 0.1f),
                )
            }

            AeroIllustration.GLASS_ORB -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.9f),
                            tokens.sky.auroraPrimary.copy(alpha = 0.55f),
                            Color(0x55204A6A),
                        ),
                        center = Offset(cx - r * 0.3f, cy - r * 0.35f),
                        radius = r * 1.8f,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = r * 0.22f,
                    center = Offset(cx - r * 0.4f, cy - r * 0.42f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f),
                )
                // Orbiting eco leaf.
                val angle = float * TAU
                val ox = cx + cos(angle) * r * 1.5f
                val oy = cy + sin(angle) * r * 0.55f
                val leaf = Path().apply {
                    moveTo(ox, oy - r * 0.26f)
                    cubicTo(
                        ox + r * 0.35f, oy - r * 0.30f,
                        ox + r * 0.38f, oy + r * 0.22f,
                        ox, oy + r * 0.26f,
                    )
                    cubicTo(
                        ox - r * 0.38f, oy + r * 0.22f,
                        ox - r * 0.35f, oy - r * 0.30f,
                        ox, oy - r * 0.26f,
                    )
                    close()
                }
                drawPath(leaf, color = tokens.safeEmerald)
                drawPath(leaf, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 1f))
            }
        }
    }
}

/** Small helper used by the settings preview strip. */
@Composable
fun AeroDewdropRow(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AeroDewdrop(Modifier, 12.dp)
    }
}

/**
 * Sparkle burst that drifts outward while the success ripples expand — the final flourish of a
 * successful install. One Canvas draw, no per-sparkle composables.
 */
@Composable
fun AeroCelebration(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    sparks: Int = 10,
) {
    if (!active) return
    val tokens = LocalAeroTokens.current ?: return
    val transition = rememberInfiniteTransition(label = "aero-celebration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "aero-celebration-phase",
    )
    Canvas(modifier = modifier) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f
        for (i in 0 until sparks) {
            val p = (phase + i * (1f / sparks)) % 1f
            val angle = (i * 36f + p * 24f) * (PI / 180.0).toFloat()
            val radius = maxR * (0.45f + 0.55f * p)
            val alpha = sin(PI * p).toFloat().coerceIn(0f, 1f) * 0.9f
            val sparkCenter = Offset(
                center.x + cos(angle) * radius,
                center.y + sin(angle) * radius,
            )
            val r = (2.2f + 3.4f * p) * density
            drawCircle(color = Color.White.copy(alpha = alpha), radius = r, center = sparkCenter)
            drawCircle(
                color = tokens.activeCyan.copy(alpha = alpha * 0.8f),
                radius = r * 0.45f,
                center = sparkCenter,
            )
        }
    }
}
