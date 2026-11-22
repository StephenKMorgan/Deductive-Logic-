package com.therefore.logic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.therefore.logic.data.ThemeMode
import com.therefore.logic.data.AccentTheme
import com.therefore.logic.data.TextSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The app's colors: ink-blue accent on cool paper, plus good / bad / hint states. */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val sunk: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color,
    val good: Color, val goodSoft: Color, val bad: Color, val badSoft: Color,
    val hint: Color, val hintSoft: Color, val star: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFF2F4F8), surface = Color(0xFFFFFFFF), sunk = Color(0xFFE8EBF3), ink = Color(0xFF141A2E),
    muted = Color(0xFF5B6480), line = Color(0xFFD9DEEA), accent = Color(0xFF2F45C9), onAccent = Color.White,
    accentSoft = Color(0xFFE3E7FB), good = Color(0xFF17734A), goodSoft = Color(0xFFDEF2E7), bad = Color(0xFFB3352C),
    badSoft = Color(0xFFFBE5E2), hint = Color(0xFF8A5A00), hintSoft = Color(0xFFFBEFD5), star = Color(0xFFD39A12),
)

val DarkPalette = Palette(
    bg = Color(0xFF10131F), surface = Color(0xFF181C2C), sunk = Color(0xFF0C0F19), ink = Color(0xFFE7EAF5),
    muted = Color(0xFF98A1BF), line = Color(0xFF2A3048), accent = Color(0xFF8D9DFF), onAccent = Color(0xFF10131F),
    accentSoft = Color(0xFF232A50), good = Color(0xFF5CCF96), goodSoft = Color(0xFF15301F), bad = Color(0xFFFF8C80),
    badSoft = Color(0xFF3A1C1B), hint = Color(0xFFF0C060), hintSoft = Color(0xFF33280F), star = Color(0xFFF0C060),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Accent, soft accent and the color drawn on top of the accent, for light and dark. */
private data class Accent(val light: Color, val lightSoft: Color, val onLight: Color, val dark: Color, val darkSoft: Color)

private val ACCENTS = mapOf(
    AccentTheme.BLUE to Accent(Color(0xFF2F45C9), Color(0xFFE3E7FB), Color.White, Color(0xFF8D9DFF), Color(0xFF232A50)),
    AccentTheme.TEAL to Accent(Color(0xFF0F766E), Color(0xFFDDF3F0), Color.White, Color(0xFF5EEAD4), Color(0xFF13332F)),
    AccentTheme.PURPLE to Accent(Color(0xFF6D28D9), Color(0xFFEDE6FB), Color.White, Color(0xFFC4B5FD), Color(0xFF2C2350)),
    AccentTheme.ROSE to Accent(Color(0xFFBE185D), Color(0xFFFBE4EE), Color.White, Color(0xFFF9A8D4), Color(0xFF3D1A2B)),
    AccentTheme.AMBER to Accent(Color(0xFFB45309), Color(0xFFFCEFD9), Color.White, Color(0xFFFCD34D), Color(0xFF3A2A10)),
    AccentTheme.FOREST to Accent(Color(0xFF166534), Color(0xFFDFF3E6), Color.White, Color(0xFF86EFAC), Color(0xFF14301E)),
)

/** The light or dark palette painted in [accent]. */
fun paletteFor(dark: Boolean, accent: AccentTheme): Palette {
    val a = ACCENTS.getValue(accent)
    return if (dark) DarkPalette.copy(accent = a.dark, accentSoft = a.darkSoft, onAccent = DarkPalette.bg)
    else LightPalette.copy(accent = a.light, accentSoft = a.lightSoft, onAccent = a.onLight)
}

/** The swatch shown in settings. */
fun accentSwatch(accent: AccentTheme, dark: Boolean): Color = ACCENTS.getValue(accent).let { if (dark) it.dark else it.light }

/** Formulas shown as plain text (~ & v ->) instead of symbols. */
val LocalPlainText = staticCompositionLocalOf { false }

val Mono = FontFamily.Monospace

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    )
}

val FormulaStyle = TextStyle(fontFamily = Mono, fontSize = 16.sp)

@Composable
fun ThereforeTheme(
    mode: ThemeMode,
    plainText: Boolean,
    accent: AccentTheme = AccentTheme.BLUE,
    textSize: TextSize = TextSize.NORMAL,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val p = paletteFor(dark, accent)
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink, surfaceVariant = p.sunk,
        onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.bad, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, surfaceContainerHighest = p.sunk,
        surfaceContainerLowest = p.surface, secondaryContainer = p.accentSoft, surfaceTint = p.accent,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink, surfaceVariant = p.sunk,
        onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.bad, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, surfaceContainerHighest = p.sunk,
        surfaceContainerLowest = p.surface, secondaryContainer = p.accentSoft, surfaceTint = p.accent,
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides p, LocalPlainText provides plainText,
        LocalDensity provides Density(density.density, density.fontScale * textSize.scale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

private val PLAIN = mapOf('¬' to "~", '∧' to "&", '∨' to "v", '→' to "->", '↔' to "<->")

/** Applies the notation setting to any text containing formula symbols. */
fun String.inNotation(plain: Boolean): String =
    if (!plain) this else buildString { for (ch in this@inNotation) append(PLAIN[ch] ?: ch) }
