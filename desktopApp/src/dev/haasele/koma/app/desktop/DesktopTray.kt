package dev.haasele.koma.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.SystemTray as AwtSystemTray
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * System tray entry so the window can close without stopping the engine.
 *
 * On modern Linux/Wayland, AWT [AwtSystemTray] is usually unsupported (XEmbed only).
 * We fall back to dorkbox AppIndicator / StatusNotifierItem.
 *
 * Tray init is deferred and runs off the UI thread — [SystemTray.get] can block for
 * tens of seconds on Wayland while probing GTK/D-Bus, which used to delay first paint.
 */
@Composable
fun ApplicationScope.DesktopTray(
    onReady: (Boolean) -> Unit = {},
    onOpen: () -> Unit,
    onQuit: () -> Unit,
) {
    val isLinux = remember {
        System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)
    }

    when {
        isLinux -> {
            // Never call AwtSystemTray.isSupported() here — it forces early AWT init.
            DorkboxTray(onReady = onReady, onOpen = onOpen, onQuit = onQuit)
        }
        runCatching { AwtSystemTray.isSupported() }.getOrDefault(false) && isTraySupported -> {
            LaunchedEffect(Unit) { onReady(true) }
            Tray(
                icon = PulseIcon,
                tooltip = "Uptime Koma",
                onAction = onOpen,
                menu = {
                    Item("Open", onClick = onOpen)
                    Item("Quit", onClick = onQuit)
                },
            )
        }
        else -> {
            LaunchedEffect(Unit) { onReady(false) }
        }
    }
}

@Composable
private fun DorkboxTray(
    onReady: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onQuit: () -> Unit,
) {
    val trayRef = remember { AtomicReference<SystemTray?>(null) }

    LaunchedEffect(Unit) {
        // Let the Compose window paint first.
        delay(500)
        val tray = withContext(Dispatchers.IO) { createDorkboxTray() }
        if (tray == null) {
            System.err.println(
                "koma-desktop: no StatusNotifier/AppIndicator tray available; " +
                    "window close will minimize instead of quitting.",
            )
            onReady(false)
            return@LaunchedEffect
        }
        trayRef.set(tray)

        fun onUi(block: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) block()
            else SwingUtilities.invokeLater(block)
        }

        val setup = runCatching {
            tray.setTooltip("Uptime Koma")
            tray.setImage(pulseAwtImage())
            tray.menu.add(MenuItem("Open") { onUi(onOpen) })
            tray.menu.add(Separator())
            tray.menu.add(MenuItem("Quit") { onUi(onQuit) })
        }
        if (setup.isFailure) {
            System.err.println("koma-desktop: tray setup failed: ${setup.exceptionOrNull()?.message}")
            runCatching { tray.remove() }
            trayRef.set(null)
            onReady(false)
            return@LaunchedEffect
        }

        System.err.println("koma-desktop: tray ready (${tray.type})")
        onReady(true)
    }

    DisposableEffect(Unit) {
        onDispose {
            trayRef.getAndSet(null)?.let { runCatching { it.remove() } }
        }
    }
}

/**
 * Prefer StatusNotifier (D-Bus, no GTK) on Linux; fall back to AppIndicator / auto-detect.
 * Avoid AUTO_FIX_INCONSISTENCIES — it probes many backends and can hang for ~1 min.
 * Each attempt is hard-capped so a hung native probe cannot stall the UI forever.
 */
private fun createDorkboxTray(): SystemTray? {
    SystemTray.AUTO_FIX_INCONSISTENCIES = false
    SystemTray.PREFER_GTK3 = false

    val attempts = listOf(
        SystemTray.TrayType.AppIndicator,
        SystemTray.TrayType.AutoDetect,
    )
    for (type in attempts) {
        val tray = tryGetTray(type, timeoutSeconds = 4)
        if (tray != null) return tray
    }
    return null
}

private fun tryGetTray(type: SystemTray.TrayType, timeoutSeconds: Long): SystemTray? {
    val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "koma-tray-$type").apply { isDaemon = true }
    }
    return try {
        executor.submit<SystemTray?> {
            SystemTray.FORCE_TRAY_TYPE = type
            SystemTray.get("Uptime Koma")
        }.get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        System.err.println("koma-desktop: tray type $type timed out after ${timeoutSeconds}s")
        null
    } catch (t: Throwable) {
        System.err.println("koma-desktop: tray type $type failed (${t.message})")
        null
    } finally {
        executor.shutdownNow()
    }
}

private fun pulseAwtImage(): java.awt.image.BufferedImage {
    val size = 64
    val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
    )
    g.color = java.awt.Color(0x2D, 0xD4, 0xA7)
    g.fillOval(0, 0, size, size)
    g.color = java.awt.Color.WHITE
    val stroke = (size / 9f).coerceAtLeast(1f)
    g.stroke = java.awt.BasicStroke(
        stroke,
        java.awt.BasicStroke.CAP_ROUND,
        java.awt.BasicStroke.JOIN_ROUND,
    )
    val midY = size / 2f
    val path = java.awt.geom.Path2D.Float().apply {
        moveTo(size * 0.18f, midY)
        lineTo(size * 0.38f, midY)
        lineTo(size * 0.50f, size * 0.24f)
        lineTo(size * 0.64f, size * 0.78f)
        lineTo(size * 0.74f, midY)
        lineTo(size * 0.86f, midY)
    }
    g.draw(path)
    g.dispose()
    return image
}

private object PulseIcon : Painter() {
    override val intrinsicSize: Size = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        val accent = Color(0xFF2DD4A7)
        drawCircle(accent, radius = size.minDimension / 2f)
        val stroke = size.minDimension / 9f
        val midY = size.height / 2f
        drawLine(Color.White, Offset(size.width * 0.18f, midY), Offset(size.width * 0.38f, midY), stroke)
        drawLine(Color.White, Offset(size.width * 0.38f, midY), Offset(size.width * 0.5f, size.height * 0.24f), stroke)
        drawLine(Color.White, Offset(size.width * 0.5f, size.height * 0.24f), Offset(size.width * 0.64f, size.height * 0.78f), stroke)
        drawLine(Color.White, Offset(size.width * 0.64f, size.height * 0.78f), Offset(size.width * 0.74f, midY), stroke)
        drawLine(Color.White, Offset(size.width * 0.74f, midY), Offset(size.width * 0.86f, midY), stroke)
    }
}
