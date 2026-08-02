package dev.haasele.koma.app.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import dev.haasele.koma.app.nav.NavAction

/**
 * Motion tokens adapted from Uptime Kuma (`src/assets/vars.scss` + `app.scss`):
 * - `$easing-in` / `$easing-out` / `$easing-in-out`
 * - page `slide-fade` at 0.2s
 * - HeartbeatBar slide at 0.25s ease-in-out
 */
object KomaMotion {

    /** Kuma `$easing-in: cubic-bezier(0.54, 0.78, 0.55, 0.97)` */
    val easingIn: Easing = CubicBezierEasing(0.54f, 0.78f, 0.55f, 0.97f)

    /** Kuma `$easing-out: cubic-bezier(0.25, 0.46, 0.45, 0.94)` */
    val easingOut: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

    /** Kuma `$easing-in-out: cubic-bezier(0.79, 0.14, 0.15, 0.86)` */
    val easingInOut: Easing = CubicBezierEasing(0.79f, 0.14f, 0.15f, 0.86f)

    /** Standard CSS `ease-in-out` used by HeartbeatBar slide. */
    val easeInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Page / content transitions — Kuma `slide-fade` duration. */
    const val PageMs = 200

    /** Status colour / metric / chart value tweens — long enough to read. */
    const val StatusMs = 320

    /** HeartbeatBar left-slide when a new beat arrives. */
    const val BeatSlideMs = 280

    /** Chart path / ping sparkline settle. */
    const val ChartMs = 650

    fun <T> page() = tween<T>(durationMillis = PageMs, easing = easingIn)

    fun <T> beatSlide() = tween<T>(durationMillis = BeatSlideMs, easing = easeInOut)

    fun <T> status() = tween<T>(durationMillis = StatusMs, easing = easingIn)

    fun <T> chart() = tween<T>(durationMillis = ChartMs, easing = easingOut)

    fun <T> quick() = tween<T>(durationMillis = 150, easing = easingOut)

    /** Soft crossfade for auth shell and content value swaps. */
    fun contentCrossfade(): ContentTransform =
        fadeIn(animationSpec = page()) togetherWith fadeOut(animationSpec = quick())

    /**
     * Screen changes:
     * - Push/Pop: horizontal
     * - Tab jumps: vertical, direction follows drawer order (down the list vs up)
     */
    fun screenTransition(action: NavAction): ContentTransform = when (action) {
        NavAction.Push, NavAction.Replace -> {
            (fadeIn(animationSpec = page()) + slideInHorizontally(
                animationSpec = page(),
                initialOffsetX = { it / 10 },
            )) togetherWith (fadeOut(animationSpec = quick()) + slideOutHorizontally(
                animationSpec = page(),
                targetOffsetX = { -it / 14 },
            ))
        }
        NavAction.Pop -> {
            (fadeIn(animationSpec = page()) + slideInHorizontally(
                animationSpec = page(),
                initialOffsetX = { -it / 14 },
            )) togetherWith (fadeOut(animationSpec = quick()) + slideOutHorizontally(
                animationSpec = page(),
                targetOffsetX = { it / 10 },
            ))
        }
        NavAction.TabDown -> {
            // Toward a lower drawer entry: new screen rises from below.
            (fadeIn(animationSpec = page()) + slideInVertically(
                animationSpec = page(),
                initialOffsetY = { it / 6 },
            )) togetherWith (fadeOut(animationSpec = quick()) + slideOutVertically(
                animationSpec = page(),
                targetOffsetY = { -it / 10 },
            ))
        }
        NavAction.TabUp -> {
            // Toward a higher drawer entry: new screen drops in from above.
            (fadeIn(animationSpec = page()) + slideInVertically(
                animationSpec = page(),
                initialOffsetY = { -it / 6 },
            )) togetherWith (fadeOut(animationSpec = quick()) + slideOutVertically(
                animationSpec = page(),
                targetOffsetY = { it / 10 },
            ))
        }
    }
}
