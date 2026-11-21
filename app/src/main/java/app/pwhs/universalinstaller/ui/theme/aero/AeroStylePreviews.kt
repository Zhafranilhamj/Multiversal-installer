package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

@Composable
internal fun StylePreviewCard(
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
internal fun MaterialPreview() {
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
internal fun AeroPreview(tokens: AeroTokens) {
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

