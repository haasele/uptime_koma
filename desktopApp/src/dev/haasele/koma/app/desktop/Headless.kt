package dev.haasele.koma.app.desktop

import dev.haasele.koma.shared.CliManagedServer
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.DesktopPaths
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.engine.EngineEvent
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Runs the engine without a UI — used by `--nogui` / `--port` on JAR, binary, AppImage and Flatpak.
 * The embedded HTTP(S) API is always started in this mode.
 */
fun runHeadless(cli: DesktopCli) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val core = KomaCore.create(
        driverFactory = JvmDatabaseDriverFactory(DesktopPaths.databaseFile),
        parentContext = Dispatchers.Default,
        localNotifier = NoopLocalNotifier,
    )

    val stopped = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log("Shutting down")
            runBlocking { core.shutdown() }
            scope.cancel()
            stopped.countDown()
        },
    )

    runBlocking {
        val settings = core.settings.get()
        core.engine.start()
        val port = cli.port ?: settings.embeddedServerPort
        val tlsPath = cli.httpsCertPath
        val started = core.embeddedServer.start(
            port = port,
            tlsCertificatePath = tlsPath,
            hostnames = cli.hostnames,
        )
        if (!started) {
            log("ERROR: failed to bind embedded server on port $port" +
                (tlsPath?.let { " (TLS $it)" } ?: ""))
            exitProcess(1)
        }

        val bound = core.embeddedServer.port ?: port
        val scheme = if (core.embeddedServer.isTls) "https" else "http"
        val wsScheme = if (core.embeddedServer.isTls) "wss" else "ws"
        val publicHosts = cli.hostnames.ifEmpty { Platform.localHostNames.ifEmpty { listOf("127.0.0.1") } }
        core.markCliManagedServer(
            CliManagedServer(
                scheme = scheme,
                port = bound,
                hostnames = publicHosts,
                tls = core.embeddedServer.isTls,
            ),
        )
        log("Database: ${DesktopPaths.databaseFile}")
        log("Monitors: ${core.monitors.getAll().count { it.active }} active")
        log("Bound $scheme://0.0.0.0:$bound")
        publicHosts.forEach { host ->
            log("  $scheme://$host:$bound")
        }
        if (isKomaDebugEnabled()) {
            log("Debug logging enabled")
        }
        log(
            if (settings.remoteAccessEnabled) {
                val remoteHost = publicHosts.first().takeUnless { it == "0.0.0.0" } ?: "<host>"
                "Remote UI: $wsScheme://$remoteHost:$bound/api/remote"
            } else {
                "Remote UI disabled — enable it in the desktop app (or settings DB) to connect a phone"
            },
        )
    }

    scope.launch {
        core.engine.events.collect { event ->
            when (event) {
                is EngineEvent.StatusChanged -> {
                    val name = core.monitors.getById(event.monitorId)?.name ?: "monitor ${event.monitorId}"
                    log("$name is now ${event.to.name.lowercase()}")
                }
                is EngineEvent.BeatRecorded -> {
                    if (isKomaDebugEnabled()) {
                        val name = core.monitors.getById(event.monitorId)?.name ?: "monitor ${event.monitorId}"
                        log("debug: $name beat ${event.heartbeat.status} ${event.heartbeat.message}")
                    }
                }
                is EngineEvent.RunningChanged -> {
                    if (isKomaDebugEnabled()) log("debug: engine running=${event.running}")
                }
            }
        }
    }

    stopped.await()
}

private fun log(message: String) {
    println("[${nowMs().formatDateTime()}] $message")
}
