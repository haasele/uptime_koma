package dev.haasele.koma.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported

/**
 * Keeping a tray entry is what lets the window close without stopping the checks, which is the
 * whole point of running the engine on a desktop.
 */
@Composable
fun ApplicationScope.DesktopTray(onOpen: () -> Unit, onQuit: () -> Unit) {
    if (!isTraySupported) return
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

private object PulseIcon : Painter() {
    override val intrinsicSize: Size = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        val accent = Color(0xFF2DD4A7)
        drawCircle(accent, radius = size.minDimension / 2f)
        val stroke = size.minDimension / 9f
        val midY = size.height / 2f
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.18f, midY),
            end = Offset(size.width * 0.38f, midY),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.38f, midY),
            end = Offset(size.width * 0.5f, size.height * 0.24f),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.5f, size.height * 0.24f),
            end = Offset(size.width * 0.64f, size.height * 0.78f),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.64f, size.height * 0.78f),
            end = Offset(size.width * 0.74f, midY),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.74f, midY),
            end = Offset(size.width * 0.86f, midY),
            strokeWidth = stroke,
        )
    }
}
