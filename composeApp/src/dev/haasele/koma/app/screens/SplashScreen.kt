package dev.haasele.koma.app.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.theme.DefaultAccentHex
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.theme.KomaTheme
import dev.haasele.koma.app.theme.ThemeIds
import kotlin.math.PI
import kotlin.math.sin

/**
 * Full-bleed animated splash. Safe to show before [dev.haasele.koma.shared.KomaCore] exists —
 * uses the ambient [MaterialTheme] (or [BootSplashScreen] for a default dark brand wrap).
 */
@Composable
fun SplashScreen(
    message: String = "Starting…",
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val transition = rememberInfiniteTransition(label = "splash")

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-glow",
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val titleAlpha by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "title-alpha",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.surface,
                        accent.copy(alpha = 0.14f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(96.dp)
                        .scale(pulse)
                        .clip(RoundedCornerShape(28.dp))
                        .background(accent.copy(alpha = glow)),
                )
                Box(
                    Modifier
                        .size(72.dp)
                        .scale(pulse)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KomaIcons.Pulse,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(titleAlpha),
            ) {
                Text(
                    "UPTIME",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "KOMA",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    color = scheme.onBackground,
                )
            }

            Spacer(Modifier.height(28.dp))

            Canvas(Modifier.fillMaxWidth(0.55f).height(36.dp)) {
                val path = Path()
                val midY = size.height / 2f
                val amp = size.height * 0.32f
                val steps = 48
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = size.width * t
                    val y = midY + sin(t * 4f * PI + wavePhase).toFloat() * amp *
                        (0.35f + 0.65f * sin(t * PI).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = accent.copy(alpha = 0.22f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round),
                )
                drawPath(
                    path = path,
                    color = accent.copy(alpha = 0.85f),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                )
            }

            Spacer(Modifier.height(36.dp))

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Canvas(Modifier.width(160.dp).height(3.dp)) {
                val track = scheme.outlineVariant.copy(alpha = 0.45f)
                drawRoundRect(
                    color = track,
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    size = size,
                )
                val barW = size.width * 0.38f
                val travel = (size.width - barW).coerceAtLeast(0f)
                val x = travel * shimmer
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, accent, Color.Transparent),
                        startX = x,
                        endX = x + barW,
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
    }
}

/** Splash wrapped in the default brand theme — for desktop cold start before settings load. */
@Composable
fun BootSplashScreen(
    message: String = "Starting…",
    modifier: Modifier = Modifier,
) {
    KomaTheme(themePreference = ThemeIds.Dark, accentHex = DefaultAccentHex) {
        SplashScreen(message = message, modifier = modifier)
    }
}

@Composable
fun LoadingScreen() {
    SplashScreen(message = "Opening the local database")
}
