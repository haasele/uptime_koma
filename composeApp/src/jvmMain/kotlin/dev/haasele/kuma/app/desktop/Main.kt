package dev.haasele.koma.app.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.configureSwingGlobalsForCompose
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window as ComposeDesktopWindow
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import dev.haasele.koma.app.KomaApp
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.DesktopPaths
import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.notify.DesktopNotifier
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Desktop UI / headless entry after Wayland env is ready.
 * Prefer [DesktopLauncher] as the JAR / jpackage main class.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun runDesktop(args: Array<String>) {
    WaylandAwtBootstrap.apply()

    if (args.contains("--headless")) {
        runHeadless(args)
        return
    }

    System.setProperty("sun.java2d.uiScale.enabled", "false")
    System.setProperty("sun.java2d.uiScale", "1.0")
    System.setProperty("skiko.linux.autodpi", "false")
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "SOFTWARE")
    System.setProperty("compose.application.configure.swing.globals", "false")
    configureSwingGlobalsForCompose(useAutoDpiOnLinux = false)

    runWindowed()
}

/** @deprecated Use [DesktopLauncher]; kept for IDE run configs that still point here. */
@Deprecated("Use DesktopLauncher.main", ReplaceWith("DesktopLauncher.main(args)"))
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopLauncher.main(args)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun runWindowed() = application {
    val core = remember {
        KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(DesktopPaths.databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = DesktopNotifier(),
        )
    }

    var visible by remember { mutableStateOf(true) }
    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))

    DesktopTray(
        onOpen = { visible = true },
        onQuit = {
            runBlocking { core.shutdown() }
            exitApplication()
        },
    )

    LaunchedEffect(core) { core.start() }

    ComposeDesktopWindow(
        onCloseRequest = {
            if (isTraySupported) {
                visible = false
            } else {
                runBlocking { core.shutdown() }
                exitApplication()
            }
        },
        visible = visible,
        title = "Uptime Koma",
        state = windowState,
    ) {
        DisposableEffect(window) {
            window.background = java.awt.Color(11, 18, 20)
            window.contentPane.background = java.awt.Color(11, 18, 20)
            fun syncContentBounds() {
                val pane = window.contentPane ?: return
                pane.layout = BorderLayout()
                val w = pane.width.coerceAtLeast(1)
                val h = pane.height.coerceAtLeast(1)
                if (w <= 1 || h <= 1) return
                pane.components.forEach { child: Component ->
                    child.setBounds(0, 0, w, h)
                }
                pane.revalidate()
                pane.repaint()
            }
            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = syncContentBounds()
                override fun componentShown(e: ComponentEvent?) = syncContentBounds()
            }
            window.addComponentListener(listener)
            syncContentBounds()
            onDispose { window.removeComponentListener(listener) }
        }

        LaunchedEffect(windowState.size, visible) {
            val pane = window.contentPane ?: return@LaunchedEffect
            pane.layout = BorderLayout()
            val w = pane.width.coerceAtLeast(1)
            val h = pane.height.coerceAtLeast(1)
            pane.components.forEach { child: Component ->
                child.setBounds(0, 0, w, h)
            }
            pane.revalidate()
            pane.repaint()
        }

        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
            Box(Modifier.fillMaxSize()) {
                KomaApp(core)
            }
        }
    }
}
