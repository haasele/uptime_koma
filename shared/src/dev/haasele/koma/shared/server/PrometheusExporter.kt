package dev.haasele.koma.shared.server

import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.domain.MonitorStatus

/** Exposes the same metric names Uptime Koma publishes so existing dashboards keep working. */
class PrometheusExporter(private val core: KomaCore) {

    suspend fun render(): String = buildString {
        appendLine("# HELP monitor_status Current status: 0 down, 1 up, 2 pending, 3 maintenance")
        appendLine("# TYPE monitor_status gauge")
        appendLine("# HELP monitor_response_time Latest response time in milliseconds")
        appendLine("# TYPE monitor_response_time gauge")
        appendLine("# HELP monitor_cert_days_remaining Days until the TLS certificate expires")
        appendLine("# TYPE monitor_cert_days_remaining gauge")

        val monitors = core.monitors.getAll()
        for (monitor in monitors) {
            val beat = core.heartbeats.last(monitor.id)
            val labels = buildString {
                append("monitor_name=\"").append(monitor.name.escapeLabel()).append('"')
                append(",monitor_type=\"").append(monitor.type.id).append('"')
                append(",monitor_url=\"").append(monitor.displayTarget.escapeLabel()).append('"')
            }
            val status = beat?.status ?: MonitorStatus.PENDING
            appendLine("monitor_status{$labels} ${status.code}")
            beat?.pingMs?.let { appendLine("monitor_response_time{$labels} $it") }
        }

        appendLine("# HELP koma_monitor_count Number of configured monitors")
        appendLine("# TYPE koma_monitor_count gauge")
        appendLine("koma_monitor_count ${monitors.size}")
        appendLine("# HELP koma_engine_running Whether the monitoring engine is polling")
        appendLine("# TYPE koma_engine_running gauge")
        appendLine("koma_engine_running ${if (core.engine.running.value) 1 else 0}")
    }

    private fun String.escapeLabel(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
