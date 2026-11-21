package app.pwhs.core.domain

/**
 * Selectable visual style for the app.
 *
 * [MATERIAL_EXPRESSIVE] is the untouched default: the Material 3 / Expressive look the
 * installer has always shipped with. [FRUTIGER_AERO] is the optional mid-2000s glossy
 * glass style. The value is persisted by name in DataStore, like [ThemeMode].
 */
enum class UiStyle(val label: String) {
    MATERIAL_EXPRESSIVE("Material Expressive"),
    FRUTIGER_AERO("Frutiger Aero");

    companion object {
        fun from(stored: String?): UiStyle =
            entries.firstOrNull { it.name == stored } ?: MATERIAL_EXPRESSIVE
    }
}

/**
 * Sub-palettes for [UiStyle.FRUTIGER_AERO]. Each one drives the living background,
 * the aurora ribbons and the accent gel controls.
 */
enum class AeroPreset(val label: String) {
    /** Classic bright cerulean, ice glass, aqua and eco-green accents. */
    CRYSTAL_SKY("Crystal Sky"),

    /** Turquoise, seafoam glass, coral-sunlight highlights. */
    TROPICAL_LAGOON("Tropical Lagoon"),

    /** Emerald meadow, sunlit dew, teal sky. */
    AURORA_FOREST("Aurora Forest"),

    /** Signature night Aero: abyssal navy, northern-lights emerald and electric cyan. */
    DEEP_BIOLUMINESCENCE("Deep Bioluminescence");

    companion object {
        fun from(stored: String?): AeroPreset =
            entries.firstOrNull { it.name == stored } ?: CRYSTAL_SKY
    }
}

/** How much procedural decoration the Aero background draws. */
enum class AeroDecorationLevel(val label: String) {
    /** Animated sky/aurora ribbons, rising bubbles and dew/caustic glints. */
    FULL("Full"),

    /** Subtle glass reflections and a few ambient orbs. */
    BALANCED("Balanced"),

    /** Glassmorphic surfaces and gel controls only, static background. */
    MINIMAL("Minimal");

    companion object {
        fun from(stored: String?): AeroDecorationLevel =
            entries.firstOrNull { it.name == stored } ?: BALANCED
    }
}

/**
 * Everything besides light/dark that the Aero style needs at the theme level.
 * Persisted in DataStore; passed through [app.pwhs.core.ui.theme.UniversalInstallerTheme]
 * so every surface can read it from a composition local instead of re-reading prefs.
 */
data class AeroUiConfig(
    val preset: AeroPreset = AeroPreset.CRYSTAL_SKY,
    val decorationLevel: AeroDecorationLevel = AeroDecorationLevel.BALANCED,
    val glassBlur: Boolean = true,
    val reducedMotion: Boolean = false,
    /** Filled in by the theme from the resolved Light/Dark/System mode. */
    val darkMode: Boolean = false,
)
