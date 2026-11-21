package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radius of [shape] in pixels, clamped so a pill stays a pill. */
private fun DrawScope.cornerPx(shape: Shape): Float {
    val raw = if (shape is RoundedCornerShape) {
        shape.topStart.toPx(size, this)
    } else {
        size.minDimension / 2f
    }
    return raw.coerceIn(0f, size.minDimension / 2f)
}

/**
 * Three-layer frosted glass: translucent vertical body, a wet meniscus along the top edge and
 * a hairline specular rim. Shadows are tinted, never neutral black.
 */
fun Modifier.aeroGlass(
    tokens: AeroTokens,
    shape: Shape = RoundedCornerShape(26.dp),
    frost: Color = Color.Unspecified,
    elevation: Dp = 10.dp,
    rimWidth: Dp = 1.dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = tokens.cardShadow,
        spotColor = tokens.cardShadow,
    )
    .clip(shape)
    .drawBehind {
        val radius = cornerPx(shape)
        val corners = CornerRadius(radius, radius)
        if (frost != Color.Unspecified) {
            drawRoundRect(color = frost, cornerRadius = corners)
        }
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(tokens.glassTop, tokens.glassBottom),
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corners,
        )
        // Thick wet meniscus along the upper curve.
        val meniscusWidth = 2.dp.toPx()
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to tokens.meniscus,
                0.22f to tokens.meniscus.copy(alpha = tokens.meniscus.alpha * 0.35f),
                0.42f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
            topLeft = Offset(meniscusWidth / 2f, meniscusWidth / 2f),
            size = Size(
                (size.width - meniscusWidth).coerceAtLeast(1f),
                (size.height - meniscusWidth).coerceAtLeast(1f),
            ),
            cornerRadius = CornerRadius(
                (radius - meniscusWidth / 2f).coerceAtLeast(0f),
                (radius - meniscusWidth / 2f).coerceAtLeast(0f),
            ),
            style = Stroke(width = meniscusWidth),
        )
        // Outer hairline: bright top-left catchlight fading to aqua bottom-right.
        val hairline = rimWidth.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(tokens.rimTop, tokens.rimMid, tokens.rimBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            topLeft = Offset(hairline / 2f, hairline / 2f),
            size = Size(
                (size.width - hairline).coerceAtLeast(1f),
                (size.height - hairline).coerceAtLeast(1f),
            ),
            cornerRadius = CornerRadius(
                (radius - hairline / 2f).coerceAtLeast(0f),
                (radius - hairline / 2f).coerceAtLeast(0f),
            ),
            style = Stroke(width = hairline),
        )
    }

/**
 * Skeuomorphic gel fill: saturated jewel gradient, the signature glossy reflection band across
 * the top half, a soft bottom bounce light and a bright glass rim.
 */
fun Modifier.aeroGel(
    tokens: AeroTokens,
    colors: List<Color>,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    glossIntensity: Float = 1f,
): Modifier = this
    .clip(shape)
    .drawBehind {
        val radius = cornerPx(shape)
        val corners = CornerRadius(radius, radius)
        drawRoundRect(brush = Brush.verticalGradient(colors), cornerRadius = corners)
        if (enabled) {
            val inset = 2.dp.toPx()
            val bandHeight = (size.height * 0.48f).coerceAtLeast(1f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        tokens.glossTop.copy(alpha = tokens.glossTop.alpha * glossIntensity),
                        tokens.glossBottom.copy(alpha = tokens.glossBottom.alpha * glossIntensity),
                    ),
                    startY = inset,
                    endY = inset + bandHeight,
                ),
                topLeft = Offset(inset, inset),
                size = Size(
                    width = (size.width - inset * 2f).coerceAtLeast(1f),
                    height = bandHeight,
                ),
                cornerRadius = corners,
            )
            // Bounce light along the lower inner edge.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.72f to Color.Transparent,
                    0.97f to tokens.bounceLight,
                    startY = 0f,
                    endY = size.height,
                ),
                topLeft = Offset(inset, inset),
                size = Size(
                    width = (size.width - inset * 2f).coerceAtLeast(1f),
                    height = (size.height - inset * 2f).coerceAtLeast(1f),
                ),
                cornerRadius = corners,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        val rim = 1.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(tokens.rimTop, tokens.rimMid, tokens.rimBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            topLeft = Offset(rim / 2f, rim / 2f),
            size = Size(
                (size.width - rim).coerceAtLeast(1f),
                (size.height - rim).coerceAtLeast(1f),
            ),
            cornerRadius = corners,
            style = Stroke(width = rim),
        )
    }

/** Recessed frosted trough for switches, progress tracks and segmented controls. */
fun Modifier.aeroRecessedTrough(
    tokens: AeroTokens,
    shape: Shape = RoundedCornerShape(50),
): Modifier = this
    .clip(shape)
    .drawBehind {
        val radius = cornerPx(shape)
        val corners = CornerRadius(radius, radius)
        drawRoundRect(brush = Brush.verticalGradient(listOf(tokens.trackOuter.copy(alpha = 0.55f), Color.Transparent)), cornerRadius = corners)
        drawRoundRect(color = tokens.trackInner, cornerRadius = corners)
        // Top inner shadow …
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.22f),
                0.30f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corners,
            style = Stroke(width = 2.dp.toPx()),
        )
        // … and a lower catchlight so it reads as glass, not paint.
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.72f to Color.Transparent,
                0.98f to tokens.trackOuter,
                startY = 0f,
                endY = size.height,
            ),
            cornerRadius = corners,
            style = Fill,
        )
        drawRoundRect(
            color = tokens.hairline,
            cornerRadius = corners,
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/** Snappy squishy press feedback for gel controls and glass cards. */
@Composable
fun Modifier.aeroPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalAeroMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && motion) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow * 1.6f,
        ),
        label = "aero-press-scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered list entrance: each row fades up 24dp, delayed by its position so a freshly loaded
 * list ripples in instead of snapping. Returns [Modifier] untouched for the Material style.
 */
@Composable
fun Modifier.aeroEntrance(index: Int = 0): Modifier {
    if (LocalAeroTokens.current == null) return this
    val motion = LocalAeroMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motion) 380 else 0,
            delayMillis = if (motion) index.coerceIn(0, 8) * 45 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "aero-entrance",
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }
}
