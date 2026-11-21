package app.pwhs.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.AeroPreset

/**
 * Sky + accent stops for one Aero preset. The app layer turns these into the full
 * [app.pwhs.core.ui.theme] token set; :core keeps them so :tv and :wearos can draw
 * a matching wash without depending on :app.
 */
data class AeroSkyPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyHorizon: Color,
    val skyBottom: Color,
    val auroraPrimary: Color,
    val auroraSecondary: Color,
    val auroraHighlight: Color,
    val sunBloom: Color,
)

/** Light Aero skies are luminous daylight; dark Aero skies are Midnight Aurora. */
fun aeroSkyPalette(preset: AeroPreset, dark: Boolean): AeroSkyPalette = when (preset) {
    AeroPreset.CRYSTAL_SKY -> if (dark) {
        AeroSkyPalette(
            skyTop = Color(0xFF041526),
            skyMid = Color(0xFF062338),
            skyHorizon = Color(0xFF073451),
            skyBottom = Color(0xFF052A3F),
            auroraPrimary = Color(0xFF2EC5FF),
            auroraSecondary = Color(0xFF42FFD0),
            auroraHighlight = Color(0xFFE8FBFF),
            sunBloom = Color(0xFF2FA8D8),
        )
    } else {
        AeroSkyPalette(
            skyTop = Color(0xFFBFE9FF),
            skyMid = Color(0xFFE4F6FF),
            skyHorizon = Color(0xFFF8FCFF),
            skyBottom = Color(0xFFDDF3EF),
            auroraPrimary = Color(0xFF7FDFFF),
            auroraSecondary = Color(0xFF9CFFD9),
            auroraHighlight = Color(0xFFFFFFFF),
            sunBloom = Color(0xFFFFF4C9),
        )
    }

    AeroPreset.TROPICAL_LAGOON -> if (dark) {
        AeroSkyPalette(
            skyTop = Color(0xFF04201F),
            skyMid = Color(0xFF06302E),
            skyHorizon = Color(0xFF0B3C38),
            skyBottom = Color(0xFF08262B),
            auroraPrimary = Color(0xFF1FE0C4),
            auroraSecondary = Color(0xFF7CF5B1),
            auroraHighlight = Color(0xFFFFE9C2),
            sunBloom = Color(0xFFFFB27A),
        )
    } else {
        AeroSkyPalette(
            skyTop = Color(0xFFCDF6F4),
            skyMid = Color(0xFFE6FBF4),
            skyHorizon = Color(0xFFFFF7EE),
            skyBottom = Color(0xFFEAF4FF),
            auroraPrimary = Color(0xFF66E0D4),
            auroraSecondary = Color(0xFFA8F0C8),
            auroraHighlight = Color(0xFFFFE3A8),
            sunBloom = Color(0xFFFFD9A8),
        )
    }

    AeroPreset.AURORA_FOREST -> if (dark) {
        AeroSkyPalette(
            skyTop = Color(0xFF03180F),
            skyMid = Color(0xFF052418),
            skyHorizon = Color(0xFF08301E),
            skyBottom = Color(0xFF04221B),
            auroraPrimary = Color(0xFF43F5A0),
            auroraSecondary = Color(0xFFB6FF7A),
            auroraHighlight = Color(0xFFE4FFD0),
            sunBloom = Color(0xFF7CE0A8),
        )
    } else {
        AeroSkyPalette(
            skyTop = Color(0xFFDDF5E3),
            skyMid = Color(0xFFF0FBF2),
            skyHorizon = Color(0xFFF9FFF9),
            skyBottom = Color(0xFFE4F8F1),
            auroraPrimary = Color(0xFF8FE6B0),
            auroraSecondary = Color(0xFFD6FF9E),
            auroraHighlight = Color(0xFFFFF3C4),
            sunBloom = Color(0xFFFFF0BD),
        )
    }

    AeroPreset.DEEP_BIOLUMINESCENCE -> if (dark) {
        AeroSkyPalette(
            skyTop = Color(0xFF020E20),
            skyMid = Color(0xFF041A30),
            skyHorizon = Color(0xFF06294A),
            skyBottom = Color(0xFF031B2E),
            auroraPrimary = Color(0xFF34F5D0),
            auroraSecondary = Color(0xFF39C6FF),
            auroraHighlight = Color(0xFFB8FFF0),
            sunBloom = Color(0xFF22D3EE),
        )
    } else {
        AeroSkyPalette(
            skyTop = Color(0xFFCDEDF5),
            skyMid = Color(0xFFE4F6FA),
            skyHorizon = Color(0xFFEFFBFF),
            skyBottom = Color(0xFFE6F2FF),
            auroraPrimary = Color(0xFF46C8B8),
            auroraSecondary = Color(0xFF5EE0FF),
            auroraHighlight = Color(0xFFB6FFE8),
            sunBloom = Color(0xFFBDEEDC),
        )
    }
}

/** Accent + gel colors shared by every preset (the preset only shifts the sky). */
object AeroAccents {
    val PrimaryGelTop = Color(0xFF36D1FF)
    val PrimaryGelMid = Color(0xFF0088E8)
    val PrimaryGelBottom = Color(0xFF0066CC)
    val SuccessGelTop = Color(0xFF5CE86E)
    val SuccessGelBottom = Color(0xFF18B84A)
    val DangerGelTop = Color(0xFFFF6B81)
    val DangerGelBottom = Color(0xFFD91E48)
    val SafeEmerald = Color(0xFF16C784)
    val WarningAmber = Color(0xFFF5A524)
    val DangerRuby = Color(0xFFE01E5A)
    val ActiveCyan = Color(0xFF00C8FF)
}

/**
 * Material color scheme for Aero. Background is transparent on purpose: the app draws its
 * living sky canvas behind every Scaffold, and glass surfaces sit on top of it.
 */
fun aeroColorScheme(dark: Boolean, preset: AeroPreset): ColorScheme {
    val sky = aeroSkyPalette(preset, dark)
    return if (dark) {
        darkColorScheme(
            primary = Color(0xFF4FD8FF),
            onPrimary = Color(0xFF00263A),
            primaryContainer = Color(0xFF0B4A66),
            onPrimaryContainer = Color(0xFFD6F6FF),
            secondary = Color(0xFF63EBB8),
            onSecondary = Color(0xFF00382A),
            secondaryContainer = Color(0xFF0A4C3A),
            onSecondaryContainer = Color(0xFFCFFBEA),
            tertiary = Color(0xFF7BE7FF),
            onTertiary = Color(0xFF003344),
            tertiaryContainer = Color(0xFF0B4457),
            onTertiaryContainer = Color(0xFFD8F8FF),
            error = Color(0xFFFF8FA3),
            onError = Color(0xFF4C0011),
            errorContainer = Color(0xFF6A0E22),
            onErrorContainer = Color(0xFFFFDDE3),
            background = Color.Transparent,
            onBackground = Color(0xFFF0FAFF),
            surface = Color(0xB80D2C4A),
            onSurface = Color(0xFFF0FAFF),
            surfaceVariant = Color(0x992A5070),
            onSurfaceVariant = Color(0xFFB7D6E8),
            outline = Color(0xFF4C7A96),
            outlineVariant = Color(0x6626536E),
            surfaceContainerLowest = Color(0xC2091B2E),
            surfaceContainerLow = Color(0xB80A243D),
            surfaceContainer = Color(0xC00C2C48),
            surfaceContainerHigh = Color(0xC80E3454),
            surfaceContainerHighest = Color(0xD0123D60),
            inverseSurface = Color(0xFFE8F6FF),
            inverseOnSurface = Color(0xFF06263C),
            inversePrimary = Color(0xFF00649A),
            scrim = Color(0xCC000F1E),
            surfaceTint = sky.auroraPrimary,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0088E8),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC9EBFF),
            onPrimaryContainer = Color(0xFF043A5C),
            secondary = Color(0xFF0FA97E),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCFF7E8),
            onSecondaryContainer = Color(0xFF044434),
            tertiary = Color(0xFF0E9AA7),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFC9F6FB),
            onTertiaryContainer = Color(0xFF00363C),
            error = Color(0xFFD91E48),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDDE3),
            onErrorContainer = Color(0xFF5C0018),
            background = Color.Transparent,
            onBackground = Color(0xFF082A47),
            surface = Color(0xE6F4FBFF),
            onSurface = Color(0xFF082A47),
            surfaceVariant = Color(0xD9E4F5FD),
            onSurfaceVariant = Color(0xFF26536F),
            outline = Color(0xFF5F9DBF),
            outlineVariant = Color(0x66A9CFE2),
            surfaceContainerLowest = Color(0xF2FBFFFF),
            surfaceContainerLow = Color(0xEBF4FBFF),
            surfaceContainer = Color(0xE6EDF8FF),
            surfaceContainerHigh = Color(0xE3E6F4FE),
            surfaceContainerHighest = Color(0xE0DFF0FC),
            inverseSurface = Color(0xFF0A2438),
            inverseOnSurface = Color(0xFFE8F7FF),
            inversePrimary = Color(0xFF7FDFFF),
            scrim = Color(0x55041C33),
            surfaceTint = sky.auroraPrimary,
        )
    }
}

/** Softer, extra-rounded shapes so Aero surfaces read as gel/glass lozenges. */
val AeroShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
