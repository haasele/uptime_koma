package dev.haasele.koma.app.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.rememberWindowState
import dev.haasele.koma.app.KomaApp
import dev.haasele.koma.app.screens.BootSplashScreen
import dev.haasele.koma.app.theme.KomaMotion
import dev.haasele.koma.app.theme.resolveKomaBackgroundArgb
import dev.haasele.koma.shared.CliManagedServer
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.DesktopPaths
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.AppSettings
import dev.haasele.koma.shared.notify.DesktopNotifier
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.max
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Desktop UI entry after Wayland env is ready.
 * Prefer [DesktopLauncher] as the JAR / jpackage / AppImage / Flatpak main class.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun runDesktop(cli: DesktopCli) {
    WaylandAwtBootstrap.apply()

    System.setProperty("sun.java2d.uiScale.enabled", "false")
    System.setProperty("sun.java2d.uiScale", "1.0")
    System.setProperty("skiko.linux.autodpi", "false")
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "SOFTWARE")
    System.setProperty("compose.application.configure.swing.globals", "false")
    configureSwingGlobalsForCompose(useAutoDpiOnLinux = false)

    // Optional CLI TLS/port overrides for the GUI session's embedded server.
    runWindowed(cli)
}

/** @deprecated Use [DesktopLauncher]; kept for IDE run configs that still point here. */
@Deprecated("Use DesktopLauncher.main", ReplaceWith("DesktopLauncher.main(args)"))
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopLauncher.main(args)
}

/** Minimum time the boot splash stays visible so cold-start work feels intentional. */
private const val MinSplashMs = 1_100L

@OptIn(ExperimentalComposeUiApi::class, ExperimentalAnimationApi::class)
private fun runWindowed(cli: DesktopCli) = application {
    val notifier = remember { DesktopNotifier() }
    var core by remember { mutableStateOf<KomaCore?>(null) }
    var splashMessage by remember { mutableStateOf("Starting…") }

    var visible by remember { mutableStateOf(true) }
    var trayReady by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))
    val settings by (core?.settings?.observe() ?: flowOf(AppSettings()))
        .collectAsState(AppSettings())
    val backdropArgb = remember(settings.theme, settings.accentColor, core == null) {
        if (core == null) {
            resolveKomaBackgroundArgb("dark", "#2DD4A7")
        } else {
            resolveKomaBackgroundArgb(settings.theme, settings.accentColor)
        }
    }

    fun quitApp() {
        runBlocking { core?.shutdown() }
        exitApplication()
    }

    // Paint the window first; open the DB / wire the engine off the composition path.
    LaunchedEffect(Unit) {
        splashMessage = "Opening the local database…"
        lateinit var created: KomaCore
        val openMs = measureTimeMillis {
            created = withContext(Dispatchers.IO) {
                KomaCore.create(
                    driverFactory = JvmDatabaseDriverFactory(DesktopPaths.databaseFile),
                    parentContext = Dispatchers.Default,
                    localNotifier = notifier,
                )
            }
        }
        splashMessage = "Starting engine…"
        val startMs = measureTimeMillis {
            created.start()
            if (cli.managesEmbeddedServer) {
                created.embeddedServer.stop()
                val listenPort = cli.port ?: created.settings.get().embeddedServerPort
                val tls = cli.httpsCertPath
                val ok = created.embeddedServer.start(
                    port = listenPort,
                    tlsCertificatePath = tls,
                    hostnames = cli.hostnames,
                )
                if (!ok) {
                    System.err.println("koma-desktop: failed to bind embedded server on port $listenPort")
                } else {
                    val bound = created.embeddedServer.port ?: listenPort
                    val scheme = if (created.embeddedServer.isTls) "https" else "http"
                    val hosts = cli.hostnames.ifEmpty {
                        Platform.localHostNames.ifEmpty { listOf("127.0.0.1") }
                    }
                    created.markCliManagedServer(
                        CliManagedServer(
                            scheme = scheme,
                            port = bound,
                            hostnames = hosts,
                            tls = created.embeddedServer.isTls,
                        ),
                    )
                }
            }
        }
        val remaining = max(0L, MinSplashMs - openMs - startMs)
        if (remaining > 0) delay(remaining)
        core = created
    }

    ComposeDesktopWindow(
        onCloseRequest = {
            when {
                trayReady -> visible = false
                // No tray (common on Wayland without SNI): keep the engine, park in the task switcher.
                else -> windowState.isMinimized = true
            }
        },
        visible = visible,
        title = "Uptime Koma",
        state = windowState,
    ) {
        // No Compose MenuBar — only the native window chrome (close / minimize / maximize).
        // Quit remains available via the system tray when present.

        DisposableEffect(window, backdropArgb) {
            val awt = java.awt.Color(backdropArgb, true)
            window.background = awt
            window.contentPane.background = awt
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
                AnimatedContent(
                    targetState = core,
                    contentKey = { if (it == null) "splash" else "app" },
                    transitionSpec = { KomaMotion.contentCrossfade() },
                    label = "boot",
                ) { current ->
                    if (current == null) {
                        BootSplashScreen(message = splashMessage)
                    } else {
                        KomaApp(current)
                    }
                }
            }
        }
    }

    // After the window so tray probing cannot block first paint.
    DesktopTray(
        onReady = { trayReady = it },
        onOpen = {
            visible = true
            windowState.isMinimized = false
        },
        onQuit = ::quitApp,
    )
}
