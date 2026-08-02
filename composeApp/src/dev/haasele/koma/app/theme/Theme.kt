package dev.haasele.koma.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.haasele.koma.shared.domain.MonitorStatus
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

private val Emerald = Color(0xFF2DD4A7)
private val EmeraldDeep = Color(0xFF0E7C63)
private val Amber = Color(0xFFF5A524)
private val Slate950 = Color(0xFF0B1015)
private val Slate900 = Color(0xFF121A21)
private val Slate800 = Color(0xFF1B252E)
private val Slate700 = Color(0xFF2A3742)
private val Mist50 = Color(0xFFF6F8F9)
private val Mist100 = Color(0xFFECF1F3)

const val DefaultAccentHex = "#2DD4A7"

/** Persisted theme ids used by Settings → Design. */
object ThemeIds {
    const val Dark = "dark"
    const val Light = "light"
    const val MaterialYouDark = "material_you_dark"
    const val MaterialYouLight = "material_you_light"

    val all = listOf(Dark, Light, MaterialYouDark, MaterialYouLight)

    fun label(id: String): String = when (id) {
        Dark -> "Default (Dark)"
        Light -> "Light"
        MaterialYouDark -> "Material You (Dark)"
        MaterialYouLight -> "Material You (Light)"
        "system" -> "Default (Dark)" // legacy
        else -> id
    }

    fun normalize(id: String): String = when (id) {
        "system" -> Dark
        MaterialYouDark, MaterialYouLight, Light, Dark -> id
        else -> Dark
    }
}

private val BrandDarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Slate950,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = Color.White,
    secondary = Amber,
    onSecondary = Slate950,
    background = Slate950,
    onBackground = Color(0xFFE4EBF0),
    surface = Slate900,
    onSurface = Color(0xFFE4EBF0),
    surfaceVariant = Slate800,
    onSurfaceVariant = Color(0xFFA7B6C2),
    outline = Slate700,
    outlineVariant = Color(0xFF223039),
    error = Color(0xFFEF5350),
)

private val BrandLightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3E8),
    onPrimaryContainer = Color(0xFF04352A),
    secondary = Color(0xFFB4761A),
    onSecondary = Color.White,
    background = Mist50,
    onBackground = Color(0xFF16212A),
    surface = Color.White,
    onSurface = Color(0xFF16212A),
    surfaceVariant = Mist100,
    onSurfaceVariant = Color(0xFF4C5C68),
    outline = Color(0xFFCBD6DD),
    outlineVariant = Color(0xFFE2E9ED),
    error = Color(0xFFC0392B),
)

/** Status colours stay identical in both schemes so the meaning never shifts. */
data class StatusPalette(
    val up: Color = Color(0xFF22C55E),
    val down: Color = Color(0xFFEF4444),
    val pending: Color = Color(0xFFF59E0B),
    val maintenance: Color = Color(0xFF3B82F6),
    val paused: Color = Color(0xFF6B7280),
) {
    fun colorFor(status: MonitorStatus): Color = when (status) {
        MonitorStatus.UP -> up
        MonitorStatus.DOWN -> down
        MonitorStatus.PENDING -> pending
        MonitorStatus.MAINTENANCE -> maintenance
    }
}

val LocalStatusPalette = staticCompositionLocalOf { StatusPalette() }

private val KomaTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

@Composable
fun KomaTheme(
    themePreference: String = ThemeIds.Dark,
    accentHex: String = DefaultAccentHex,
    content: @Composable () -> Unit,
) {
    val theme = ThemeIds.normalize(themePreference)
    val accent = remember(accentHex) { parseAccent(accentHex) }
    val scheme = remember(theme, accent) { colorSchemeFor(theme, accent) }

    CompositionLocalProvider(LocalStatusPalette provides StatusPalette()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KomaTypography,
            content = content,
        )
    }
}

private fun colorSchemeFor(theme: String, accent: Color): ColorScheme = when (theme) {
    ThemeIds.Light -> brandScheme(BrandLightColors, accent, dark = false)
    ThemeIds.MaterialYouDark -> materialYouScheme(accent, dark = true)
    ThemeIds.MaterialYouLight -> materialYouScheme(accent, dark = false)
    else -> brandScheme(BrandDarkColors, accent, dark = true)
}

/** Resolved Material colour scheme for the given design prefs (e.g. AWT window chrome). */
fun resolveKomaColorScheme(themePreference: String, accentHex: String): ColorScheme =
    colorSchemeFor(ThemeIds.normalize(themePreference), parseAccent(accentHex))

/** Brand surfaces: accent drives primary and lightly tints the whole chrome. */
private fun brandScheme(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF0B1015) else Color.White
    val container = if (dark) accent.darken(0.35f) else accent.lighten(0.55f)
    val onContainer = if (dark) Color.White else accent.darken(0.45f)
    val neutrals = tonalNeutrals(accent, dark, chroma = if (dark) 0.12f else 0.08f)
    val secondary = accent.shiftHue(28f).let { if (dark) it.lighten(0.08f) else it.darken(0.12f) }
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = if (secondary.luminance() > 0.45f) Color(0xFF0B1015) else Color.White,
        secondaryContainer = if (dark) secondary.darken(0.35f) else secondary.lighten(0.55f),
        onSecondaryContainer = if (dark) secondary.lighten(0.50f) else secondary.darken(0.40f),
        background = neutrals.background,
        onBackground = neutrals.onSurface,
        surface = neutrals.surface,
        onSurface = neutrals.onSurface,
        surfaceVariant = neutrals.surfaceVariant,
        onSurfaceVariant = neutrals.onSurfaceVariant,
        outline = neutrals.outline,
        outlineVariant = neutrals.outlineVariant,
        inversePrimary = if (dark) accent.lighten(0.18f) else accent.darken(0.12f),
        surfaceContainerLowest = neutrals.containerLowest,
        surfaceContainerLow = neutrals.containerLow,
        surfaceContainer = neutrals.container,
        surfaceContainerHigh = neutrals.containerHigh,
        surfaceContainerHighest = neutrals.containerHighest,
    )
}

/**
 * Material You–style tonal palette derived from [seed].
 * Cross-platform stand-in for wallpaper dynamic colour (desktop / iOS / older Android).
 * Background / surfaces keep the seed hue so the whole UI shifts with the accent.
 */
private fun materialYouScheme(seed: Color, dark: Boolean): ColorScheme {
    val primary = if (dark) seed.lighten(0.12f) else seed.darken(0.08f)
    val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF101418) else Color.White
    val primaryContainer = if (dark) seed.darken(0.40f) else seed.lighten(0.62f)
    val onPrimaryContainer = if (dark) seed.lighten(0.55f) else seed.darken(0.42f)
    val secondary = seed.shiftHue(40f).let { if (dark) it.lighten(0.10f) else it.darken(0.10f) }
    val tertiary = seed.shiftHue(-40f).let { if (dark) it.lighten(0.08f) else it.darken(0.12f) }
    val neutrals = tonalNeutrals(seed, dark, chroma = if (dark) 0.22f else 0.16f)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = if (secondary.luminance() > 0.45f) Color(0xFF101418) else Color.White,
            secondaryContainer = secondary.darken(0.35f),
            onSecondaryContainer = secondary.lighten(0.55f),
            tertiary = tertiary,
            onTertiary = if (tertiary.luminance() > 0.45f) Color(0xFF101418) else Color.White,
            tertiaryContainer = tertiary.darken(0.35f),
            onTertiaryContainer = tertiary.lighten(0.55f),
            background = neutrals.background,
            onBackground = neutrals.onSurface,
            surface = neutrals.surface,
            onSurface = neutrals.onSurface,
            surfaceVariant = neutrals.surfaceVariant,
            onSurfaceVariant = neutrals.onSurfaceVariant,
            outline = neutrals.outline,
            outlineVariant = neutrals.outlineVariant,
            inversePrimary = seed.lighten(0.22f),
            surfaceContainerLowest = neutrals.containerLowest,
            surfaceContainerLow = neutrals.containerLow,
            surfaceContainer = neutrals.container,
            surfaceContainerHigh = neutrals.containerHigh,
            surfaceContainerHighest = neutrals.containerHighest,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = if (secondary.luminance() > 0.45f) Color(0xFF101418) else Color.White,
            secondaryContainer = secondary.lighten(0.55f),
            onSecondaryContainer = secondary.darken(0.40f),
            tertiary = tertiary,
            onTertiary = if (tertiary.luminance() > 0.45f) Color(0xFF101418) else Color.White,
            tertiaryContainer = tertiary.lighten(0.55f),
            onTertiaryContainer = tertiary.darken(0.40f),
            background = neutrals.background,
            onBackground = neutrals.onSurface,
            surface = neutrals.surface,
            onSurface = neutrals.onSurface,
            surfaceVariant = neutrals.surfaceVariant,
            onSurfaceVariant = neutrals.onSurfaceVariant,
            outline = neutrals.outline,
            outlineVariant = neutrals.outlineVariant,
            inversePrimary = seed.darken(0.10f),
            surfaceContainerLowest = neutrals.containerLowest,
            surfaceContainerLow = neutrals.containerLow,
            surfaceContainer = neutrals.container,
            surfaceContainerHigh = neutrals.containerHigh,
            surfaceContainerHighest = neutrals.containerHighest,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

/** Hue-locked neutrals so background / cards / drawer shift with the accent. */
private data class TonalNeutrals(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
)

private fun tonalNeutrals(seed: Color, dark: Boolean, chroma: Float): TonalNeutrals {
    val h = seed.toHsl()[0]
    fun tone(lightness: Float, sat: Float = chroma) = hslToColor(h, sat.coerceIn(0f, 0.45f), lightness, 1f)
    return if (dark) {
        TonalNeutrals(
            background = tone(0.07f),
            surface = tone(0.10f),
            surfaceVariant = tone(0.15f, chroma * 0.85f),
            onSurface = tone(0.92f, 0.04f),
            onSurfaceVariant = tone(0.72f, 0.06f),
            outline = tone(0.28f, chroma * 0.6f),
            outlineVariant = tone(0.18f, chroma * 0.5f),
            containerLowest = tone(0.05f),
            containerLow = tone(0.09f),
            container = tone(0.12f),
            containerHigh = tone(0.15f),
            containerHighest = tone(0.18f),
        )
    } else {
        TonalNeutrals(
            background = tone(0.97f),
            surface = tone(0.995f, chroma * 0.5f),
            surfaceVariant = tone(0.93f),
            onSurface = tone(0.12f, 0.05f),
            onSurfaceVariant = tone(0.32f, 0.06f),
            outline = tone(0.72f, chroma * 0.5f),
            outlineVariant = tone(0.86f, chroma * 0.4f),
            containerLowest = tone(1.0f, chroma * 0.25f),
            containerLow = tone(0.98f, chroma * 0.4f),
            container = tone(0.96f),
            containerHigh = tone(0.94f),
            containerHighest = tone(0.91f),
        )
    }
}

fun parseAccent(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return Emerald
    val value = cleaned.toLongOrNull(16) ?: return Emerald
    return Color(value or 0xFF000000L)
}

fun Color.toHexRgb(): String {
    fun ch(v: Float) = ((v.coerceIn(0f, 1f) * 255).toInt() and 0xFF).toString(16).padStart(2, '0')
    return "#${ch(red)}${ch(green)}${ch(blue)}"
}

/** Hue in degrees 0..360 for accent pickers. */
fun Color.hueDegrees(): Float = toHsl()[0]

/** Saturated accent from a hue wheel position (Material-You friendly). */
fun accentFromHue(hueDegrees: Float): Color =
    hslToColor(((hueDegrees % 360f) + 360f) % 360f, 0.62f, 0.48f, 1f)

private fun Color.darken(amount: Float): Color {
    val f = (1f - amount).coerceIn(0f, 1f)
    return Color(red * f, green * f, blue * f, alpha)
}

private fun Color.lighten(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * a,
        green = green + (1f - green) * a,
        blue = blue + (1f - blue) * a,
        alpha = alpha,
    )
}

private fun Color.shiftHue(degrees: Float): Color {
    val (h, s, l) = toHsl()
    return hslToColor(((h + degrees) % 360f + 360f) % 360f, s, l, alpha)
}

private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    if ((maxC - minC).absoluteValue < 1e-5f) return floatArrayOf(0f, 0f, l)
    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h * 360f, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s <= 1e-5f) return Color(l, l, l, alpha)
    fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hk = h / 360f
    return Color(
        red = hue2rgb(p, q, hk + 1f / 3f),
        green = hue2rgb(p, q, hk),
        blue = hue2rgb(p, q, hk - 1f / 3f),
        alpha = alpha,
    )
}
