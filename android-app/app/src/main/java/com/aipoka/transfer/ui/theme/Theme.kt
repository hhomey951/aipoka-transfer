package com.aipoka.transfer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colors converted from the Aipoka/Sendly design mockup's OKLCH tokens to sRGB hex
 * (D65 OKLab -> linear sRGB -> gamma-encoded sRGB). Values were computed with a small
 * script rather than hand-eyeballed, so they should be visually accurate to the spec.
 */
object AipokaPalette {
    // Light
    val accentLight = Color(0xFFEA2B1F)
    val accentDarkGlyphLight = Color(0xFFB81F16) // "accentDark" token: icon glyphs on accentSoft chips
    val accentSoftLight = Color(0xFFFCE0DD)
    val goodLight = Color(0xFF4C9E3B) // darkened from brand A1E887 for AA contrast as icon/text
    val goodSoftLight = Color(0xFFE6F5DC)
    val goodDarkLight = Color(0xFF2F6B23)
    val dangerLight = Color(0xFFD74745)
    val inkLight = Color(0xFF141B24)
    val subLight = Color(0xFF606A74)
    val borderLight = Color(0xFFDADEE3)
    val pageBgLight = Color(0xFFF8FAFD)
    val cardBgLight = Color(0xFFFFFFFF)
    val sheetBgLight = Color(0xFFF6F9FB)
    val inputBgLight = Color(0xFFFFFFFF)
    val onAccentLight = Color(0xFFFFFFFF)

    // Dark
    val accentDarkTheme = Color(0xFFFF6357)
    val accentDarkGlyphDark = Color(0xFFFF8A7E)
    val accentSoftDark = Color(0xFF3A1512)
    val goodDark = Color(0xFFA1E887) // brand hex used directly, has enough contrast on dark bg
    val goodSoftDark = Color(0xFF1E3315)
    val goodDarkDark = Color(0xFFC3F2AE)
    val dangerDark = Color(0xFFF2716A)
    val inkDark = Color(0xFFECEFF1)
    val subDark = Color(0xFF9399A0)
    val borderDark = Color(0xFF33393E)
    val pageBgDark = Color(0xFF12171B)
    val cardBgDark = Color(0xFF20252A)
    val sheetBgDark = Color(0xFF1B2025)
    val inputBgDark = Color(0xFF292E34)
    val onAccentDark = Color(0xFF06090D)
}

/** Extra semantic colors the mockup uses that don't map 1:1 onto Material3's roles. */
data class AipokaColors(
    val accent: Color,
    val accentGlyph: Color,
    val accentSoft: Color,
    val good: Color,
    val goodSoft: Color,
    val goodDark: Color,
    val danger: Color,
    val ink: Color,
    val sub: Color,
    val border: Color,
    val pageBg: Color,
    val cardBg: Color,
    val sheetBg: Color,
    val inputBg: Color,
    val onAccent: Color
)

private val LightAipokaColors = AipokaColors(
    accent = AipokaPalette.accentLight,
    accentGlyph = AipokaPalette.accentDarkGlyphLight,
    accentSoft = AipokaPalette.accentSoftLight,
    good = AipokaPalette.goodLight,
    goodSoft = AipokaPalette.goodSoftLight,
    goodDark = AipokaPalette.goodDarkLight,
    danger = AipokaPalette.dangerLight,
    ink = AipokaPalette.inkLight,
    sub = AipokaPalette.subLight,
    border = AipokaPalette.borderLight,
    pageBg = AipokaPalette.pageBgLight,
    cardBg = AipokaPalette.cardBgLight,
    sheetBg = AipokaPalette.sheetBgLight,
    inputBg = AipokaPalette.inputBgLight,
    onAccent = AipokaPalette.onAccentLight
)

private val DarkAipokaColors = AipokaColors(
    accent = AipokaPalette.accentDarkTheme,
    accentGlyph = AipokaPalette.accentDarkGlyphDark,
    accentSoft = AipokaPalette.accentSoftDark,
    good = AipokaPalette.goodDark,
    goodSoft = AipokaPalette.goodSoftDark,
    goodDark = AipokaPalette.goodDarkDark,
    danger = AipokaPalette.dangerDark,
    ink = AipokaPalette.inkDark,
    sub = AipokaPalette.subDark,
    border = AipokaPalette.borderDark,
    pageBg = AipokaPalette.pageBgDark,
    cardBg = AipokaPalette.cardBgDark,
    sheetBg = AipokaPalette.sheetBgDark,
    inputBg = AipokaPalette.inputBgDark,
    onAccent = AipokaPalette.onAccentDark
)

val LocalAipokaColors = staticCompositionLocalOf { LightAipokaColors }

object AipokaTheme {
    val colors: AipokaColors
        @Composable get() = LocalAipokaColors.current
}

val AipokaTypography = Typography(
    titleLarge = Typography().titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Black),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Bold),
    bodyMedium = Typography().bodyMedium.copy(fontWeight = FontWeight.Normal)
)

/**
 * [darkOverride]: null = follow system dark mode (default), true/false = explicit
 * user choice from Settings > Appearance, persisted via Prefs.setAppearanceOverride.
 */
@Composable
fun AipokaAppTheme(darkOverride: Boolean?, content: @Composable () -> Unit) {
    val isDark = darkOverride ?: isSystemInDarkTheme()
    val aipokaColors = if (isDark) DarkAipokaColors else LightAipokaColors

    val materialScheme = if (isDark) {
        darkColorScheme(
            primary = aipokaColors.accent,
            onPrimary = aipokaColors.onAccent,
            secondary = aipokaColors.good,
            error = aipokaColors.danger,
            background = aipokaColors.pageBg,
            onBackground = aipokaColors.ink,
            surface = aipokaColors.cardBg,
            onSurface = aipokaColors.ink,
            surfaceVariant = aipokaColors.sheetBg,
            onSurfaceVariant = aipokaColors.sub,
            outline = aipokaColors.border
        )
    } else {
        lightColorScheme(
            primary = aipokaColors.accent,
            onPrimary = aipokaColors.onAccent,
            secondary = aipokaColors.good,
            error = aipokaColors.danger,
            background = aipokaColors.pageBg,
            onBackground = aipokaColors.ink,
            surface = aipokaColors.cardBg,
            onSurface = aipokaColors.ink,
            surfaceVariant = aipokaColors.sheetBg,
            onSurfaceVariant = aipokaColors.sub,
            outline = aipokaColors.border
        )
    }

    CompositionLocalProvider(LocalAipokaColors provides aipokaColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AipokaTypography,
            content = content
        )
    }
}
