package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        val outline = shape.createOutline(size, layoutDirection, this)
        if (frost != Color.Unspecified) {
            drawOutline(outline, brush = SolidFill(frost))
        }
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                listOf(tokens.glassTop, tokens.glassBottom),
                startY = 0f,
                endY = size.height,
            ),
        )
        // Thick wet meniscus along the upper curve.
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                0f to tokens.meniscus,
                0.22f to tokens.meniscus.copy(alpha = tokens.meniscus.alpha * 0.35f),
                0.42f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
            style = Stroke(width = 2.dp.toPx()),
        )
        // Outer hairline: bright top-left catchlight fading to aqua bottom-right.
        drawOutline(
            outline,
            brush = Brush.linearGradient(
                colors = listOf(tokens.rimTop, tokens.rimMid, tokens.rimBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = rimWidth.toPx()),
        )
    }

private fun SolidFill(color: Color): Brush = Brush.linearGradient(listOf(color, color))

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
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, brush = Brush.verticalGradient(colors))
        if (enabled) {
            val inset = 2.dp.toPx()
            val bandHeight = (size.height * 0.48f).coerceAtLeast(1f)
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
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
                cornerRadius = radius,
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
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        drawOutline(
            outline,
            brush = Brush.linearGradient(
                listOf(tokens.rimTop, tokens.rimMid, tokens.rimBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/** Recessed frosted trough for switches, progress tracks and segmented controls. */
fun Modifier.aeroRecessedTrough(
    tokens: AeroTokens,
    shape: Shape = RoundedCornerShape(50),
): Modifier = this
    .clip(shape)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, brush = SolidFill(tokens.trackInner))
        // Top inner shadow …
        drawOutline(
            outline,
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.22f),
                0.30f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
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
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            style = Fill,
        )
        drawOutline(
            outline,
            brush = SolidFill(tokens.hairline),
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
