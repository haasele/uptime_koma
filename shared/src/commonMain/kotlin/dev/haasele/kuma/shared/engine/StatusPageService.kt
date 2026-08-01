package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.data.HeartbeatRepository
import dev.haasele.koma.shared.data.MaintenanceRepository
import dev.haasele.koma.shared.data.MonitorRepository
import dev.haasele.koma.shared.data.StatusPageRepository
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.StatusPageGroupView
import dev.haasele.koma.shared.domain.StatusPageMonitorView
import dev.haasele.koma.shared.domain.StatusPageView

/** Assembles the read model a status screen renders, mirroring Uptime Koma's public page. */
class StatusPageService(
    private val statusPages: StatusPageRepository,
    private val monitors: MonitorRepository,
    private val heartbeats: HeartbeatRepository,
    private val maintenances: MaintenanceRepository,
    private val uptime: UptimeCalculator,
) {
    suspend fun view(slug: String, beatCount: Int = 50): StatusPageView? {
        val page = statusPages.getBySlug(slug) ?: return null
        return buildView(page.id, beatCount)
    }

    suspend fun viewById(pageId: Long, beatCount: Int = 50): StatusPageView? = buildView(pageId, beatCount)

    private suspend fun buildView(pageId: Long, beatCount: Int): StatusPageView? {
        val page = statusPages.getById(pageId) ?: return null
        val now = nowMs()

        val activeMaintenances = maintenances.getForStatusPage(page.id)
            .filter { MaintenanceEvaluator.isUnderMaintenance(it, now) }

        val groups = page.groups.map { group ->
            val monitorViews = group.monitorIds.mapNotNull { monitorId ->
                val monitor = monitors.getById(monitorId) ?: return@mapNotNull null
                StatusPageMonitorView(
                    monitor = monitor,
                    stats = uptime.statsFor(monitorId),
                    recentBeats = heartbeats.recent(monitorId, beatCount),
                )
            }
            StatusPageGroupView(group, monitorViews)
        }

        val allStatuses = groups.flatMap { it.monitors }.map { it.stats.currentStatus }
        val overall = when {
            allStatuses.isEmpty() -> MonitorStatus.PENDING
            allStatuses.any { it == MonitorStatus.DOWN } -> MonitorStatus.DOWN
            activeMaintenances.isNotEmpty() || allStatuses.any { it == MonitorStatus.MAINTENANCE } ->
                MonitorStatus.MAINTENANCE
            allStatuses.all { it == MonitorStatus.UP } -> MonitorStatus.UP
            else -> MonitorStatus.PENDING
        }

        return StatusPageView(
            page = page,
            pinnedIncident = statusPages.pinnedIncident(page.id),
            groups = groups,
            activeMaintenances = activeMaintenances,
            overall = overall,
        )
    }
}
