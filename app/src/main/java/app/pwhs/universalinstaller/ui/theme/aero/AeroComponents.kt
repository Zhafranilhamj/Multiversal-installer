package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pwhs.core.ui.theme.LocalAeroConfig
import kotlin.math.sin

/**
 * Paints the living Aero sky behind [content]. Renders nothing extra for the Material style,
 * which keeps every screen on its original code path.
 */
@Composable
fun AeroAppShell(
    modifier: Modifier = Modifier,
    dialog: Boolean = false,
    showCaustics: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = LocalAeroTokens.current
    if (tokens == null) {
        content()
        return
    }
    val config = LocalAeroConfig.current
    val motion = LocalAeroMotion.current
    val blur = LocalAeroBlur.current
    Box(modifier = modifier.fillMaxSize()) {
        AeroBackgroundCanvas(
            tokens = tokens,
            decoration = config.decorationLevel,
            animated = motion,
            showCaustics = showCaustics,
        )
        if (dialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.scrim),
            )
        }
        content()
    }
    if (dialog && blur) {
        // Real window blur behind the dialog, where the platform supports it (API 31+).
        app.pwhs.universalinstaller.util.WindowBlurEffect(blurRadius = 28, enabled = true)
    }
}

/** A frosted glass panel. Use [onClick] to get the squishy press feedback too. */
@Composable
fun AeroGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    frost: Color? = null,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val tokens = LocalAeroTokens.current ?: return
    val interaction = remember { MutableInteractionSource() }
    val press = if (onClick != null) {
        Modifier.aeroPressScale(interaction, pressedScale = 0.98f)
    } else {
        Modifier
    }
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(press)
            .aeroGlass(
                tokens = tokens,
                shape = shape,
                frost = frost ?: tokens.denseFrost,
                elevation = elevation,
            )
            .then(clickable)
            .padding(contentPadding),
    ) {
        content()
    }
}

/** Aqua gel pill button with the signature glossy reflection band. */
@Composable
fun AeroGelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color>? = null,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalAeroTokens.current ?: return
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val fill = when {
        !enabled -> listOf(tokens.gelDisabled, tokens.gelDisabled.copy(alpha = 0.85f))
        else -> colors ?: tokens.gelPrimary
    }
    Row(
        modifier = modifier
            .aeroPressScale(interaction, enabled = enabled)
            .aeroGel(tokens = tokens, colors = fill, enabled = enabled, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/** Glossy translucent capsule used for filter chips and pills. */
@Composable
fun AeroChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalAeroTokens.current ?: return
    val interaction = remember { MutableInteractionSource() }
    val fill = if (selected) tokens.gelPrimary else listOf(tokens.glassTop, tokens.glassBottom)
    Row(
        modifier = modifier
            .aeroPressScale(interaction, pressedScale = 0.95f)
            .aeroGel(tokens, fill, enabled = selected, shape = RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.inkOnVivid else tokens.inkPrimary,
        )
    }
}

/**
 * Glossy toggle: recessed glass trough, emerald-aqua fill when on, and a pearl thumb with
 * its own specular catchlight.
 */
@Composable
fun AeroToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalAeroTokens.current ?: return
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "aero-thumb",
    )
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = tokens.trackInner, cornerRadius = radius)
            if (checked) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(tokens.gelSuccess),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(tokens.glossTop.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    topLeft = Offset(2f, 2f),
                    size = Size(size.width - 4f, size.height * 0.46f),
                    cornerRadius = radius,
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent),
                ),
                cornerRadius = radius,
                style = Stroke(width = 2f),
            )
            drawRoundRect(
                color = tokens.hairline,
                cornerRadius = radius,
                style = Stroke(width = 1f),
            )
            // Pearl thumb.
            val thumbRadius = size.height * 0.40f
            val cx = thumbOffset.toPx() + size.height / 2f
            val cy = size.height / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFDCEEF8), Color(0xFF9FC4D8)),
                    center = Offset(cx - thumbRadius * 0.25f, cy - thumbRadius * 0.3f),
                    radius = thumbRadius * 1.7f,
                ),
                radius = thumbRadius,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = thumbRadius * 0.34f,
                center = Offset(cx - thumbRadius * 0.34f, cy - thumbRadius * 0.36f),
            )
            drawCircle(
                color = Color(0x33003355),
                radius = thumbRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )
        }
    }
}

/** Radial jewel sphere with a white crescent catchlight and a soft aura — status everywhere. */
@Composable
fun AeroStatusOrb(
    color: Color,
    modifier: Modifier = Modifier,
    orbSize: Dp = 12.dp,
    pulsing: Boolean = false,
) {
    val tokens = LocalAeroTokens.current ?: return
    val motion = LocalAeroMotion.current
    val transition = rememberInfiniteTransition(label = "aero-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "aero-orb-pulse",
    )
    Canvas(modifier = modifier.size(orbSize * 1.9f)) {
        val r = orbSize.toPx() / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        if (pulsing && motion) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.35f * pulse), Color.Transparent),
                    center = center,
                    radius = r * 1.9f,
                ),
                radius = r * 1.9f,
                center = center,
            )
        }
        drawCircle(color = color.copy(alpha = 0.18f), radius = r * 1.35f, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.95f),
                    color,
                    color.copy(alpha = 0.65f),
                ),
                center = Offset(center.x - r * 0.35f, center.y - r * 0.4f),
                radius = r * 1.9f,
            ),
            radius = r,
            center = center,
        )
        drawCircle(
            color = tokens.bubbleSpecular,
            radius = r * 0.32f,
            center = Offset(center.x - r * 0.36f, center.y - r * 0.38f),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.10f),
            radius = r,
            center = center,
            style = Stroke(width = 1f),
        )
    }
}

/** A single hanging dewdrop for glossy rims and hero cards. */
@Composable
fun AeroDewdrop(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val tokens = LocalAeroTokens.current ?: return
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(
            color = Color.Black.copy(alpha = 0.10f),
            radius = r * 0.92f,
            center = Offset(r, r + r * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f), Color(0x66DCEEFF)),
                center = Offset(r * 0.7f, r * 0.6f),
                radius = r,
            ),
            radius = r,
            center = Offset(r, r),
        )
        drawCircle(
            color = tokens.bubbleSpecular,
            radius = r * 0.30f,
            center = Offset(r * 0.64f, r * 0.55f),
        )
        drawCircle(
            color = Color(0x66FFFFFF),
            radius = r * 0.22f,
            center = Offset(r * 1.25f, r * 1.3f),
        )
    }
}

/** Small floating sparkle used for success celebrations. */
@Composable
fun AeroSparkle(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val tokens = LocalAeroTokens.current
    val resolved = if (color == Color.Unspecified) {
        tokens?.sky?.auroraHighlight ?: Color.White
    } else {
        color
    }
    Canvas(modifier = modifier.size(8.dp)) {
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val r = size.minDimension * (0.3f + progress * 0.5f)
        drawCircle(color = resolved.copy(alpha = alpha), radius = r, center = center)
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = r * 0.45f,
            center = center,
        )
    }
}

/** Thin sinusoidal caustic shimmer used on active install zones. */
@Composable
fun AeroCausticBand(modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val tokens = LocalAeroTokens.current ?: return
    val transition = rememberInfiniteTransition(label = "aero-caustic")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "aero-caustic-phase",
    )
    Canvas(modifier = modifier.fillMaxWidth().height(height * 4)) {
        for (i in 0 until 2) {
            val y = size.height * (0.4f + i * 0.25f)
            val amplitude = size.height * 0.18f
            val steps = 12
            var prev = Offset(0f, y + sin(phase * 6.2832f + i) * amplitude)
            for (step in 1..steps) {
                val x = size.width * step / steps
                val next = Offset(x, y + sin(phase * 6.2832f + step * 0.7f + i) * amplitude)
                drawLine(
                    color = tokens.caustic.copy(alpha = 0.35f),
                    start = prev,
                    end = next,
                    strokeWidth = 1.6f,
                )
                prev = next
            }
        }
    }
}
