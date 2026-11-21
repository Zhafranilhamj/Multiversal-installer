package app.pwhs.core.ui.theme

import androidx.datastore.preferences.core.Preferences
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.domain.AeroDecorationLevel
import app.pwhs.core.domain.AeroPreset
import app.pwhs.core.domain.AeroUiConfig
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.domain.UiStyle

/**
 * One read of every appearance preference, shared by every activity root so the Material
 * defaults and the optional Aero style read exactly the same keys.
 */
data class ThemeSnapshot(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val amoledMode: Boolean = false,
    val themePreset: AppThemePreset = AppThemePreset.Orange,
    val uiStyle: UiStyle = UiStyle.MATERIAL_EXPRESSIVE,
    val aeroConfig: AeroUiConfig = AeroUiConfig(),
)

fun Preferences.themeSnapshot(): ThemeSnapshot = ThemeSnapshot(
    mode = ThemeMode.entries.firstOrNull { it.name == this[SharedPrefsKeys.THEME_MODE] }
        ?: ThemeMode.System,
    dynamicColor = this[SharedPrefsKeys.DYNAMIC_COLOR] ?: false,
    amoledMode = this[SharedPrefsKeys.AMOLED_MODE] ?: false,
    themePreset = AppThemePreset.entries.firstOrNull { it.name == this[SharedPrefsKeys.THEME_PRESET] }
        ?: AppThemePreset.Orange,
    uiStyle = UiStyle.from(this[SharedPrefsKeys.UI_STYLE]),
    aeroConfig = AeroUiConfig(
        preset = AeroPreset.from(this[SharedPrefsKeys.AERO_PRESET]),
        decorationLevel = AeroDecorationLevel.from(this[SharedPrefsKeys.AERO_DECORATION_LEVEL]),
        glassBlur = this[SharedPrefsKeys.AERO_GLASS_BLUR] ?: true,
        reducedMotion = this[SharedPrefsKeys.AERO_REDUCED_MOTION] ?: false,
    ),
)
