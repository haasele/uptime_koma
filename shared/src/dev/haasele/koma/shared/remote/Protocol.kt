package dev.haasele.koma.shared.remote

import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.StatusPageView
import dev.haasele.koma.shared.domain.UptimeStats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The protocol a mobile client uses to drive a desktop engine. Deliberately modelled on the
 * app's own domain types rather than Uptime Koma's Socket.IO events; compatibility with third
 * party Koma clients is a non goal.
 */
const val REMOTE_PROTOCOL_VERSION = 1

@Serializable
sealed interface RemoteCommand {
    @Serializable @SerialName("authenticate")
    data class Authenticate(val token: String, val protocolVersion: Int = REMOTE_PROTOCOL_VERSION) : RemoteCommand

    @Serializable @SerialName("list_monitors")
    data object ListMonitors : RemoteCommand

    @Serializable @SerialName("save_monitor")
    data class SaveMonitor(val monitor: Monitor) : RemoteCommand

    @Serializable @SerialName("delete_monitor")
    data class DeleteMonitor(val monitorId: Long) : RemoteCommand

    @Serializable @SerialName("set_monitor_active")
    data class SetMonitorActive(val monitorId: Long, val active: Boolean) : RemoteCommand

    @Serializable @SerialName("list_heartbeats")
    data class ListHeartbeats(val monitorId: Long, val limit: Int = 100) : RemoteCommand

    @Serializable @SerialName("get_stats")
    data class GetStats(val monitorId: Long) : RemoteCommand

    @Serializable @SerialName("list_status_pages")
    data object ListStatusPages : RemoteCommand

    @Serializable @SerialName("status_page_view")
    data class GetStatusPageView(val slug: String) : RemoteCommand

    @Serializable @SerialName("list_notifications")
    data object ListNotifications : RemoteCommand

    @Serializable @SerialName("list_maintenances")
    data object ListMaintenances : RemoteCommand

    @Serializable @SerialName("set_maintenance_active")
    data class SetMaintenanceActive(val maintenanceId: Long, val active: Boolean) : RemoteCommand

    @Serializable @SerialName("control_engine")
    data class ControlEngine(val start: Boolean) : RemoteCommand

    @Serializable @SerialName("test_check")
    data class TestCheck(val monitor: Monitor) : RemoteCommand
}

@Serializable
sealed interface RemotePayload {
    @Serializable @SerialName("ok")
    data object Ok : RemotePayload

    @Serializable @SerialName("error")
    data class Error(val message: String) : RemotePayload

    @Serializable @SerialName("authenticated")
    data class Authenticated(val serverName: String, val protocolVersion: Int) : RemotePayload

    @Serializable @SerialName("monitors")
    data class Monitors(val monitors: List<Monitor>, val latestBeats: Map<Long, Heartbeat>) : RemotePayload

    @Serializable @SerialName("monitor_id")
    data class MonitorId(val monitorId: Long) : RemotePayload

    @Serializable @SerialName("heartbeats")
    data class Heartbeats(val monitorId: Long, val heartbeats: List<Heartbeat>) : RemotePayload

    @Serializable @SerialName("stats")
    data class Stats(val stats: UptimeStats) : RemotePayload

    @Serializable @SerialName("status_pages")
    data class StatusPages(val pages: List<StatusPage>) : RemotePayload

    @Serializable @SerialName("status_page_view")
    data class StatusPageViewPayload(val view: StatusPageView?) : RemotePayload

    @Serializable @SerialName("notifications")
    data class Notifications(val channels: List<NotificationChannel>) : RemotePayload

    @Serializable @SerialName("maintenances")
    data class Maintenances(val maintenances: List<Maintenance>) : RemotePayload

    @Serializable @SerialName("check_result")
    data class CheckOutcome(val status: MonitorStatus, val message: String, val pingMs: Long?) : RemotePayload {
        companion object {
            fun from(result: CheckResult) = CheckOutcome(result.status, result.message, result.pingMs)
        }
    }
}

@Serializable
sealed interface RemoteEvent {
    @Serializable @SerialName("beat")
    data class Beat(val monitorId: Long, val heartbeat: Heartbeat) : RemoteEvent

    @Serializable @SerialName("engine_running")
    data class EngineRunning(val running: Boolean) : RemoteEvent

    @Serializable @SerialName("monitors_changed")
    data object MonitorsChanged : RemoteEvent
}

@Serializable
sealed interface RemoteFrame {
    @Serializable @SerialName("request")
    data class Request(val id: String, val command: RemoteCommand) : RemoteFrame

    @Serializable @SerialName("response")
    data class Response(val id: String, val payload: RemotePayload) : RemoteFrame

    @Serializable @SerialName("event")
    data class Event(val event: RemoteEvent) : RemoteFrame
}
