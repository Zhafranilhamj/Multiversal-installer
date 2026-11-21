package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.ui.theme.aero.AeroEmptyIllustration
import app.pwhs.universalinstaller.ui.theme.aero.AeroGelButton
import app.pwhs.universalinstaller.ui.theme.aero.AeroIllustration
import app.pwhs.universalinstaller.ui.theme.aero.LocalAeroTokens

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val aeroTokens = LocalAeroTokens.current
    if (aeroTokens != null) {
        AeroEmptyState(
            icon = icon,
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        return
    }
    app.pwhs.core.ui.component.EmptyStateView(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/** Procedural Aero art instead of a flat grey glyph. */
@Composable
private fun AeroEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val illustration = when {
        icon.name.contains("Search", ignoreCase = true) -> AeroIllustration.DROP_RADAR
        icon.name.contains("Download", ignoreCase = true) ||
            icon.name.contains("Receipt", ignoreCase = true) ||
            icon.name.contains("History", ignoreCase = true) -> AeroIllustration.PRISM
        else -> AeroIllustration.GLASS_ORB
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        AeroEmptyIllustration(kind = illustration)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            AeroGelButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
