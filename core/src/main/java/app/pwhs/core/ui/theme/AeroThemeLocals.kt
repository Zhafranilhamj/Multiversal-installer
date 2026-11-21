package app.pwhs.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.pwhs.core.domain.AeroUiConfig
import app.pwhs.core.domain.UiStyle

/**
 * The selected visual style, exposed to every screen so it can branch its own surfaces
 * without duplicating screen logic. Defaults to the untouched Material Expressive look.
 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL_EXPRESSIVE }

/** Aero preferences (preset, decoration density, motion, resolved dark mode). */
val LocalAeroConfig = staticCompositionLocalOf { AeroUiConfig() }

/**
 * The app's own type ramp with tabular figures — APK sizes, speeds and percentages keep
 * their columns steady instead of jittering while the Aero background animates behind them.
 */
val AeroTypography: Typography = app.pwhs.core.ui.theme.Typography.let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFeatureSettings = "tnum"),
        bodyMedium = base.bodyMedium.copy(fontFeatureSettings = "tnum"),
        bodySmall = base.bodySmall.copy(fontFeatureSettings = "tnum"),
        labelLarge = base.labelLarge.copy(fontFeatureSettings = "tnum"),
        labelMedium = base.labelMedium.copy(fontFeatureSettings = "tnum"),
        labelSmall = base.labelSmall.copy(fontFeatureSettings = "tnum"),
        titleMedium = base.titleMedium.copy(fontFeatureSettings = "tnum"),
        titleSmall = base.titleSmall.copy(fontFeatureSettings = "tnum"),
    )
}

/** True when the user selected the optional Aero style. */
val isFrutigerAero: Boolean
    get() = LocalUiStyle.current == UiStyle.FRUTIGER_AERO
