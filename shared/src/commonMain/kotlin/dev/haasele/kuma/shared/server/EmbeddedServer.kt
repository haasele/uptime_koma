package dev.haasele.koma.shared.server

import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.KomaJson
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.isTcpPortFree
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.engine.EngineEvent
import dev.haasele.koma.shared.remote.RemoteCommand
import dev.haasele.koma.shared.remote.RemoteEvent
import dev.haasele.koma.shared.remote.RemoteFrame
import dev.haasele.koma.shared.remote.RemotePayload
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer as KtorEmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The always on HTTP surface: push receivers, Prometheus metrics, a JSON view of status screens
 * and the WebSocket channel a remote UI connects to.
 */
class EmbeddedServer(private val core: KomaCore) {

    private val lock = Any()
    private var server: KtorEmbeddedServer<*, *>? = null
    private var runningPort: Int? = null

    val isRunning: Boolean get() = server != null
    val port: Int? get() = runningPort

    /**
     * Binds the listening socket. Returns false when the port is busy or the platform cannot host
     * a server — callers must treat that as non fatal so a phone with a stuck port still opens.
     */
    fun start(port: Int): Boolean = synchronized(lock) {
        if (server != null) return true
        if (!Platform.supportsEmbeddedServer) return false

        val bindPort = (port..port + 20).firstOrNull { candidate ->
            candidate in 1..65535 && isTcpPortFree(candidate)
        } ?: return false

        return runCatching {
            val engine = embeddedServer(CIO, host = "0.0.0.0", port = bindPort) {
                install(WebSockets)

                routing {
                    get("/api/health") {
                        call.respondText("""{"ok":true,"engine":${core.engine.running.value}}""", contentTypeJson())
                    }

                    get("/api/push/{token}") { handlePush(call.parameters["token"]) }
                    post("/api/push/{token}") { handlePush(call.parameters["token"]) }

                    get("/metrics") {
                        if (!core.settings.get().metricsEnabled) {
                            call.respondText("metrics disabled", status = HttpStatusCode.NotFound)
                        } else {
                            call.respondText(PrometheusExporter(core).render(), contentTypeText())
                        }
                    }

                    get("/api/status-page/{slug}") {
                        val slug = call.parameters["slug"].orEmpty()
                        val view = core.statusPageService.view(slug)
                        if (view == null || !view.page.published) {
                            call.respondText("""{"error":"not found"}""", contentTypeJson(), HttpStatusCode.NotFound)
                        } else {
                            call.respondText(
                                KomaJson.encodeToString(dev.haasele.koma.shared.domain.StatusPageView.serializer(), view),
                                contentTypeJson(),
                            )
                        }
                    }

                    webSocket("/api/remote") { serveRemoteSession() }
                }
            }
            engine.start(wait = false)
            server = engine
            runningPort = bindPort
            true
        }.getOrElse {
            server = null
            runningPort = null
            false
        }
    }

    fun stop() = synchronized(lock) {
        runCatching { server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000) }
        server = null
        runningPort = null
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handlePush(token: String?) {
        val monitor = token?.let { core.monitors.getByPushToken(it) }
        if (monitor == null) {
            call.respondText("""{"ok":false,"msg":"unknown push token"}""", contentTypeJson(), HttpStatusCode.NotFound)
            return
        }

        val status = when (call.request.queryParameters["status"]?.lowercase()) {
            "down", "0" -> MonitorStatus.DOWN
            else -> MonitorStatus.UP
        }
        val message = call.request.queryParameters["msg"] ?: "OK"
        val ping = call.request.queryParameters["ping"]?.toLongOrNull()

        core.engine.recordPush(monitor, status, message, ping)
        call.respondText("""{"ok":true}""", contentTypeJson())
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.serveRemoteSession() {
        val settings = core.settings.get()
        if (!settings.remoteAccessEnabled || settings.remoteAccessToken.isBlank()) {
            sendFrame(RemoteFrame.Response("", RemotePayload.Error("Remote access is disabled")))
            close()
            return
        }

        var authenticated = false
        val eventJob = launch {
            core.engine.events.collect { event ->
                if (!authenticated) return@collect
                val remoteEvent = when (event) {
                    is EngineEvent.BeatRecorded -> RemoteEvent.Beat(event.monitorId, event.heartbeat)
                    is EngineEvent.RunningChanged -> RemoteEvent.EngineRunning(event.running)
                    is EngineEvent.StatusChanged -> RemoteEvent.MonitorsChanged
                }
                runCatching { sendFrame(RemoteFrame.Event(remoteEvent)) }
            }
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val request = runCatching {
                    KomaJson.decodeFromString(RemoteFrame.serializer(), frame.readText())
                }.getOrNull() as? RemoteFrame.Request ?: continue

                if (!authenticated) {
                    val auth = request.command as? RemoteCommand.Authenticate
                    if (auth == null || auth.token != settings.remoteAccessToken) {
                        sendFrame(RemoteFrame.Response(request.id, RemotePayload.Error("Authentication required")))
                        continue
                    }
                    authenticated = true
                    sendFrame(
                        RemoteFrame.Response(
                            request.id,
                            RemotePayload.Authenticated(Platform.name, dev.haasele.koma.shared.remote.REMOTE_PROTOCOL_VERSION),
                        ),
                    )
                    continue
                }

                val payload = runCatching { RemoteCommandHandler(core).handle(request.command) }
                    .getOrElse { RemotePayload.Error(it.message ?: "Command failed") }
                sendFrame(RemoteFrame.Response(request.id, payload))
            }
        } finally {
            eventJob.cancel()
        }
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendFrame(frame: RemoteFrame) {
        send(Frame.Text(KomaJson.encodeToString(RemoteFrame.serializer(), frame)))
    }

    private fun contentTypeJson() = io.ktor.http.ContentType.Application.Json
    private fun contentTypeText() = io.ktor.http.ContentType.Text.Plain
}

/** Executes a remote command against the local core; shared by the server and any test harness. */
class RemoteCommandHandler(private val core: KomaCore) {

    suspend fun handle(command: RemoteCommand): RemotePayload = when (command) {
        is RemoteCommand.Authenticate -> RemotePayload.Ok

        RemoteCommand.ListMonitors -> RemotePayload.Monitors(
            monitors = core.monitors.getAll(),
            latestBeats = core.monitors.getAll().mapNotNull { monitor ->
                core.heartbeats.last(monitor.id)?.let { monitor.id to it }
            }.toMap(),
        )

        is RemoteCommand.SaveMonitor -> {
            val id = core.monitors.save(command.monitor)
            core.engine.syncMonitor(id)
            RemotePayload.MonitorId(id)
        }

        is RemoteCommand.DeleteMonitor -> {
            core.monitors.delete(command.monitorId)
            core.engine.removeMonitor(command.monitorId)
            RemotePayload.Ok
        }

        is RemoteCommand.SetMonitorActive -> {
            core.monitors.setActive(command.monitorId, command.active)
            core.engine.syncMonitor(command.monitorId)
            RemotePayload.Ok
        }

        is RemoteCommand.ListHeartbeats ->
            RemotePayload.Heartbeats(command.monitorId, core.heartbeats.recent(command.monitorId, command.limit))

        is RemoteCommand.GetStats -> RemotePayload.Stats(core.uptime.statsFor(command.monitorId))

        RemoteCommand.ListStatusPages -> RemotePayload.StatusPages(core.statusPages.getAll())

        is RemoteCommand.GetStatusPageView ->
            RemotePayload.StatusPageViewPayload(core.statusPageService.view(command.slug))

        RemoteCommand.ListNotifications -> RemotePayload.Notifications(core.notifications.getAll())

        RemoteCommand.ListMaintenances -> RemotePayload.Maintenances(core.maintenances.getAll())

        is RemoteCommand.SetMaintenanceActive -> {
            core.maintenances.setManualActive(command.maintenanceId, command.active)
            RemotePayload.Ok
        }

        is RemoteCommand.ControlEngine -> {
            if (command.start) core.engine.start() else core.engine.stop()
            RemotePayload.Ok
        }

        is RemoteCommand.TestCheck -> RemotePayload.CheckOutcome.from(core.engine.testCheck(command.monitor))
    }
}
