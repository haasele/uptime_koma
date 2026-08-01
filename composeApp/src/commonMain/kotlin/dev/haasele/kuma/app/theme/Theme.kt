package dev.haasele.koma.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.haasele.koma.shared.domain.MonitorStatus

private val Emerald = Color(0xFF2DD4A7)
private val EmeraldDeep = Color(0xFF0E7C63)
private val Amber = Color(0xFFF5A524)
private val Slate950 = Color(0xFF0B1015)
private val Slate900 = Color(0xFF121A21)
private val Slate800 = Color(0xFF1B252E)
private val Slate700 = Color(0xFF2A3742)
private val Mist50 = Color(0xFFF6F8F9)
private val Mist100 = Color(0xFFECF1F3)

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
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
    themePreference: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(LocalStatusPalette provides StatusPalette()) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = KomaTypography,
            content = content,
        )
    }
}
