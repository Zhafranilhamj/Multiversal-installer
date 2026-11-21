package app.pwhs.universalinstaller.ui.theme.aero

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.pwhs.core.domain.AeroPreset
import app.pwhs.core.ui.theme.AeroAccents
import app.pwhs.core.ui.theme.AeroSkyPalette
import app.pwhs.core.ui.theme.aeroSkyPalette

/**
 * Every color the Aero surfaces draw with. Immutable and remembered once per preset/mode,
 * so draw lambdas never build brushes from scratch.
 */
@Immutable
data class AeroTokens(
    val preset: AeroPreset,
    val dark: Boolean,
    val sky: AeroSkyPalette,

    // Frosted glass
    val glassTop: Color,
    val glassBottom: Color,
    val glassFrost: Color,
    val rimTop: Color,
    val rimMid: Color,
    val rimBottom: Color,
    val meniscus: Color,
    val hairline: Color,
    val cardShadow: Color,
    val scrim: Color,
    val navGlass: Color,

    // Gel controls
    val gelPrimary: List<Color>,
    val gelSuccess: List<Color>,
    val gelDanger: List<Color>,
    val gelNeutral: List<Color>,
    val glossTop: Color,
    val glossBottom: Color,
    val bounceLight: Color,
    val gelDisabled: Color,

    // Recessed trough + liquid
    val trackInner: Color,
    val trackOuter: Color,
    val trackCatchlight: Color,
    val liquidStart: Color,
    val liquidMid: Color,
    val liquidEnd: Color,
    val liquidShimmer: Color,

    // Jewels
    val safeEmerald: Color,
    val warningAmber: Color,
    val dangerRuby: Color,
    val activeCyan: Color,

    // Ink
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkOnVivid: Color,
    val headerShadow: Color,

    // Decoration
    val bubbleFill: Color,
    val bubbleRim: Color,
    val bubbleSpecular: Color,
    val caustic: Color,
) {
    /** Deep frosted fill for dense lists — keeps WCAG AA contrast over the moving sky. */
    val denseFrost: Color
        get() = if (dark) Color(0xE60A243D) else Color(0xE6F6FBFF)

    /** Softer frost for floating bars / sheets. */
    val floatingFrost: Color
        get() = if (dark) Color(0xD90B2740) else Color(0xE6F2FAFF)
}

/** Builds the complete Aero token set for one preset in light or dark mode. */
fun aeroTokens(preset: AeroPreset, dark: Boolean): AeroTokens {
    val sky = aeroSkyPalette(preset, dark)
    return if (dark) {
        AeroTokens(
            preset = preset,
            dark = true,
            sky = sky,
            glassTop = Color(0xB80D2C4A),
            glassBottom = Color(0x8C071B30),
            glassFrost = Color(0xE60B2740),
            rimTop = Color(0xE6FFFFFF),
            rimMid = Color(0x8C9BEBFF),
            rimBottom = Color(0x4D2EC5FF),
            meniscus = Color(0xE6FFFFFF),
            hairline = Color(0x59CFEFFF),
            cardShadow = Color(0x66000D1A),
            scrim = Color(0x99000A14),
            navGlass = Color(0xCC0A2740),
            gelPrimary = listOf(Color(0xFF4FDCFF), Color(0xFF0C9CEE), Color(0xFF0066CC)),
            gelSuccess = listOf(Color(0xFF7CFF9A), Color(0xFF22D06A), Color(0xFF0E9B4C)),
            gelDanger = listOf(Color(0xFFFF8DA0), Color(0xFFEA3D63), Color(0xFFB4153C)),
            gelNeutral = listOf(Color(0xFF4A6E8C), Color(0xFF2E4A63), Color(0xFF1C3247)),
            glossTop = Color(0xADFFFFFF),
            glossBottom = Color(0x1FFFFFFF),
            bounceLight = Color(0x66B8FFFF),
            gelDisabled = Color(0xFF354A5E),
            trackInner = Color(0x99000A14),
            trackOuter = Color(0x40FFFFFF),
            trackCatchlight = Color(0x33CFEFFF),
            liquidStart = Color(0xFF22C7FF),
            liquidMid = Color(0xFF21E6C8),
            liquidEnd = Color(0xFF6BF07A),
            liquidShimmer = Color(0x80E8FBFF),
            safeEmerald = Color(0xFF2EE59D),
            warningAmber = Color(0xFFFFC24B),
            dangerRuby = Color(0xFFFF6B8B),
            activeCyan = Color(0xFF48DFFF),
            inkPrimary = Color(0xFFF0FAFF),
            inkSecondary = Color(0xFFB7D6E8),
            inkOnVivid = Color(0xFFFFFFFF),
            headerShadow = Color(0x99000A14),
            bubbleFill = sky.auroraHighlight.copy(alpha = 0.10f),
            bubbleRim = Color(0x8CE8FBFF),
            bubbleSpecular = Color(0xE6FFFFFF),
            caustic = Color(0x1FE8FBFF),
        )
    } else {
        AeroTokens(
            preset = preset,
            dark = false,
            sky = sky,
            glassTop = Color(0xD9FFFFFF),
            glassBottom = Color(0xA8E6F7FF),
            glassFrost = Color(0xEBF4FBFF),
            rimTop = Color(0xE6FFFFFF),
            rimMid = Color(0x8C9BE7FF),
            rimBottom = Color(0x4D7FD8F0),
            meniscus = Color(0xD9FFFFFF),
            hairline = Color(0x66FFFFFF),
            cardShadow = Color(0x33005E99),
            scrim = Color(0x55041C33),
            navGlass = Color(0xD9F7FCFF),
            gelPrimary = listOf(Color(0xFF36D1FF), Color(0xFF0088E8), Color(0xFF0066CC)),
            gelSuccess = listOf(Color(0xFF5CE86E), Color(0xFF2ACB5E), Color(0xFF18B84A)),
            gelDanger = listOf(Color(0xFFFF6B81), Color(0xFFF2405F), Color(0xFFD91E48)),
            gelNeutral = listOf(Color(0xFFDDEBF5), Color(0xFFC2D8E8), Color(0xFFA8C3D6)),
            glossTop = Color(0xADFFFFFF),
            glossBottom = Color(0x1FFFFFFF),
            bounceLight = Color(0x66B8FFFF),
            gelDisabled = Color(0xFFC9D9E4),
            trackInner = Color(0x33003355),
            trackOuter = Color(0x8CFFFFFF),
            trackCatchlight = Color(0x66FFFFFF),
            liquidStart = Color(0xFF00B4FF),
            liquidMid = Color(0xFF00E5CC),
            liquidEnd = Color(0xFF5EE854),
            liquidShimmer = Color(0x73FFFFFF),
            safeEmerald = AeroAccents.SafeEmerald,
            warningAmber = AeroAccents.WarningAmber,
            dangerRuby = AeroAccents.DangerRuby,
            activeCyan = AeroAccents.ActiveCyan,
            inkPrimary = Color(0xFF082A47),
            inkSecondary = Color(0xFF26536F),
            inkOnVivid = Color(0xFFFFFFFF),
            headerShadow = Color(0x55002B4D),
            bubbleFill = Color(0x1AE4F9FF),
            bubbleRim = Color(0x99FFFFFF),
            bubbleSpecular = Color(0xF2FFFFFF),
            caustic = Color(0x1FB4E9FF),
        )
    }
}
