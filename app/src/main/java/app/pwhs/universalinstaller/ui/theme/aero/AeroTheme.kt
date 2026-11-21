package app.pwhs.universalinstaller.ui.theme.aero

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.pwhs.core.domain.UiStyle
import app.pwhs.core.ui.theme.LocalAeroConfig
import app.pwhs.core.ui.theme.LocalUiStyle

/** Null whenever the selected style is not Frutiger Aero. */
val LocalAeroTokens = staticCompositionLocalOf<AeroTokens?> { null }

/** False when the user asked for low motion, or the system set animator scale to zero. */
val LocalAeroMotion = staticCompositionLocalOf { true }

/** True only when frosted blur is both enabled and supported (API 31+). */
val LocalAeroBlur = staticCompositionLocalOf { false }

/**
 * Bridges the core theme's [app.pwhs.core.domain.AeroUiConfig] into app-level tokens.
 *
 * Placed right inside [app.pwhs.core.ui.theme.UniversalInstallerTheme] at every activity
 * root, so all screens, sheets and dialogs can read the same immutable token set.
 */
@Composable
fun AeroThemeBridge(content: @Composable () -> Unit) {
    val style = LocalUiStyle.current
    val config = LocalAeroConfig.current
    val context = LocalContext.current

    val systemReducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    val isAero = style == UiStyle.FRUTIGER_AERO
    val tokens = remember(config.preset, config.darkMode, isAero) {
        if (isAero) aeroTokens(config.preset, config.darkMode) else null
    }

    CompositionLocalProvider(
        LocalAeroTokens provides tokens,
        LocalAeroMotion provides (
            isAero && !config.reducedMotion && !systemReducedMotion
            ),
        LocalAeroBlur provides (
            isAero && config.glassBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ),
        content = content,
    )
}

/** Convenience read used by screens that branch on the style. */
@Composable
fun rememberAeroTokens(): AeroTokens? = LocalAeroTokens.current

/** True when the full Aero styling should be drawn. */
val isAeroStyle: Boolean
    @Composable get() = LocalAeroTokens.current != null
