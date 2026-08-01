package dev.haasele.koma.app.desktop

import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.DesktopPaths
import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.engine.EngineEvent
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

/**
 * Runs the same engine as the windowed build without any UI, for servers and for the case where a
 * phone drives this machine over the remote protocol. Everything is configured through the desktop
 * app or the database, so the daemon only needs a data directory and a port override.
 */
fun runHeadless(args: Array<String>) {
    val portOverride = args.valueOf("--port")?.toIntOrNull()
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
        val port = portOverride ?: settings.embeddedServerPort
        if (settings.embeddedServerEnabled) core.embeddedServer.start(port)

        log("Database: ${DesktopPaths.databaseFile}")
        log("Monitors: ${core.monitors.getAll().count { it.active }} active")
        if (core.embeddedServer.isRunning) {
            log("HTTP endpoints on port $port (push, metrics, status pages)")
            log(
                if (settings.remoteAccessEnabled) {
                    "Remote UI accepted on ws://<host>:$port/api/remote"
                } else {
                    "Remote UI disabled, enable it in the desktop app to connect a phone"
                },
            )
        }
    }

    scope.launch {
        core.engine.events.collect { event ->
            if (event !is EngineEvent.StatusChanged) return@collect
            val name = core.monitors.getById(event.monitorId)?.name ?: "monitor ${event.monitorId}"
            log("$name is now ${event.to.name.lowercase()}")
        }
    }

    stopped.await()
}

private fun Array<String>.valueOf(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0 && index + 1 < size) this[index + 1] else null
}

private fun log(message: String) {
    println("[${nowMs().formatDateTime()}] $message")
}
