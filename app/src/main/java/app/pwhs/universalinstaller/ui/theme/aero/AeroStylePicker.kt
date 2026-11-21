package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.AeroDecorationLevel
import app.pwhs.core.domain.AeroPreset
import app.pwhs.core.domain.AeroUiConfig
import app.pwhs.core.domain.UiStyle
import app.pwhs.core.ui.theme.aeroSkyPalette
import app.pwhs.universalinstaller.R

/**
 * Settings → Appearance: the visual UI-style selector plus, when Aero is active, its
 * customization panel. Everything here writes straight to DataStore, so the whole app
 * changes live behind this screen.
 */
@Composable
fun UiStyleAppearanceSection(
    uiStyle: UiStyle,
    aeroConfig: AeroUiConfig,
    darkMode: Boolean,
    onStyleSelected: (UiStyle) -> Unit,
    onPresetSelected: (AeroPreset) -> Unit,
    onDecorationSelected: (AeroDecorationLevel) -> Unit,
    onGlassBlurChanged: (Boolean) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAeroTokens.current
    val titleColor = tokens?.inkPrimary ?: MaterialTheme.colorScheme.onSurface
    val subColor = tokens?.inkSecondary ?: MaterialTheme.colorScheme.onSurfaceVariant
    val materialPreviewTokens = remember(darkMode) { aeroTokens(AeroPreset.CRYSTAL_SKY, darkMode) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ui_style_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        )
        Text(
            text = stringResource(R.string.ui_style_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = subColor,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StylePreviewCard(
                selected = uiStyle == UiStyle.MATERIAL_EXPRESSIVE,
                label = stringResource(R.string.ui_style_material),
                sublabel = stringResource(R.string.ui_style_material_sub),
                modifier = Modifier.weight(1f),
                onClick = { onStyleSelected(UiStyle.MATERIAL_EXPRESSIVE) },
            ) {
                MaterialPreview()
            }
            StylePreviewCard(
                selected = uiStyle == UiStyle.FRUTIGER_AERO,
                label = stringResource(R.string.ui_style_aero),
                sublabel = stringResource(R.string.ui_style_aero_sub),
                modifier = Modifier.weight(1f),
                onClick = { onStyleSelected(UiStyle.FRUTIGER_AERO) },
            ) {
                AeroPreview(tokens = materialPreviewTokens)
            }
        }

        AnimatedVisibility(
            visible = uiStyle == UiStyle.FRUTIGER_AERO,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                expandFrom = Alignment.Top,
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(),
        ) {
            AeroCustomizationPanel(
                aeroConfig = aeroConfig,
                darkMode = darkMode,
                onPresetSelected = onPresetSelected,
                onDecorationSelected = onDecorationSelected,
                onGlassBlurChanged = onGlassBlurChanged,
                onReducedMotionChanged = onReducedMotionChanged,
            )
        }
    }
}

@Composable
private fun StylePreviewCard(
    selected: Boolean,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val tokens = LocalAeroTokens.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "style-preview-scale",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(tokens?.glassFrost ?: MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    tokens?.activeCyan ?: MaterialTheme.colorScheme.primary
                } else {
                    tokens?.hairline ?: MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            preview()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens?.gelSuccess?.first() ?: MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.ui_style_selected_cd),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens?.inkPrimary ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = tokens?.inkSecondary ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MaterialPreview() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer,
                        colors.surfaceContainer,
                        colors.secondaryContainer,
                    ),
                ),
            )
            .padding(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.surface.copy(alpha = 0.85f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.66f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.surface.copy(alpha = 0.6f)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .width(48.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primary),
                )
                Box(
                    Modifier
                        .width(30.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.secondaryContainer),
                )
            }
        }
    }
}

@Composable
private fun AeroPreview(tokens: AeroTokens) {
    Box(modifier = Modifier.fillMaxSize()) {
        AeroMiniScene(tokens = tokens, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(26.dp)
                    .aeroGlass(
                        tokens = tokens,
                        shape = RoundedCornerShape(10.dp),
                        frost = tokens.glassFrost.copy(alpha = 0.55f),
                        elevation = 2.dp,
                        rimWidth = 1.dp,
                    ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(58.dp)
                        .aeroGel(
                            tokens = tokens,
                            colors = tokens.gelPrimary,
                            shape = RoundedCornerShape(50),
                        ),
                )
                AeroDewdrop(size = 10.dp)
                Spacer(Modifier.weight(1f))
                AeroStatusOrb(color = tokens.safeEmerald, orbSize = 7.dp)
            }
        }
    }
}

@Composable
private fun AeroCustomizationPanel(
    aeroConfig: AeroUiConfig,
    darkMode: Boolean,
    onPresetSelected: (AeroPreset) -> Unit,
    onDecorationSelected: (AeroDecorationLevel) -> Unit,
    onGlassBlurChanged: (Boolean) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
) {
    val tokens = LocalAeroTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        AeroGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        text = stringResource(R.string.aero_customization_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.aero_customization_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.aero_preset_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AeroPreset.entries.forEach { preset ->
                        AeroPresetSwatch(
                            preset = preset,
                            darkMode = darkMode,
                            selected = aeroConfig.preset == preset,
                            onClick = { onPresetSelected(preset) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.aero_decoration_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                AeroDecorationSegmented(
                    selected = aeroConfig.decorationLevel,
                    onSelected = onDecorationSelected,
                )
                Text(
                    text = stringResource(R.string.aero_decoration_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AeroToggleRow(
                    title = stringResource(R.string.aero_glass_blur_title),
                    subtitle = stringResource(R.string.aero_glass_blur_subtitle),
                    checked = aeroConfig.glassBlur,
                    onCheckedChange = onGlassBlurChanged,
                )
                AeroToggleRow(
                    title = stringResource(R.string.aero_reduced_motion_title),
                    subtitle = stringResource(R.string.aero_reduced_motion_subtitle),
                    checked = aeroConfig.reducedMotion,
                    onCheckedChange = onReducedMotionChanged,
                )
                if (tokens != null) {
                    AeroLiquidProgressBar(
                        progress = 0.62f,
                        modifier = Modifier.fillMaxWidth(),
                        height = 12.dp,
                        showBubbles = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun AeroToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AeroToggleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AeroDecorationSegmented(
    selected: AeroDecorationLevel,
    onSelected: (AeroDecorationLevel) -> Unit,
) {
    val tokens = LocalAeroTokens.current ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .aeroRecessedTrough(tokens, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AeroDecorationLevel.entries.forEach { level ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isSelected) {
                            Modifier.aeroGel(tokens, tokens.gelPrimary, shape = RoundedCornerShape(50))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelected(level) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = level.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) tokens.inkOnVivid else tokens.inkPrimary,
                )
            }
        }
    }
}

@Composable
private fun AeroPresetSwatch(
    preset: AeroPreset,
    darkMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val sky = remember(preset, darkMode) { aeroSkyPalette(preset, darkMode) }
    val tokens = LocalAeroTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            sky.skyTop,
                            sky.skyMid,
                            sky.auroraSecondary.copy(alpha = 0.85f),
                        ),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = radius * 1.6f,
                    ),
                    radius = radius,
                )
                drawCircle(
                    color = if (selected) {
                        tokens?.activeCyan ?: Color.White
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    },
                    radius = radius - 1.5f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (selected) 3f else 1.5f,
                    ),
                )
                if (selected) {
                    drawCircle(
                        color = sky.auroraPrimary.copy(alpha = 0.55f),
                        radius = radius * 0.65f,
                        center = Offset(size.width * 0.55f, size.height * 0.58f),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AeroDecorationLevel.label(): String = stringResource(
    when (this) {
        AeroDecorationLevel.FULL -> R.string.aero_decoration_full
        AeroDecorationLevel.BALANCED -> R.string.aero_decoration_balanced
        AeroDecorationLevel.MINIMAL -> R.string.aero_decoration_minimal
    },
)

@Composable
private fun AeroPreset.label(): String = stringResource(
    when (this) {
        AeroPreset.CRYSTAL_SKY -> R.string.aero_preset_crystal_sky
        AeroPreset.TROPICAL_LAGOON -> R.string.aero_preset_tropical_lagoon
        AeroPreset.AURORA_FOREST -> R.string.aero_preset_aurora_forest
        AeroPreset.DEEP_BIOLUMINESCENCE -> R.string.aero_preset_deep_bioluminescence
    },
)
