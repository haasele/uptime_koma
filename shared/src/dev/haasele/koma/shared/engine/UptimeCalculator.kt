package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.data.HeartbeatRepository
import dev.haasele.koma.shared.data.StatRepository
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.UptimeStats

/**
 * Short windows are computed from raw heartbeats so a monitor shows meaningful numbers within
 * minutes; longer windows use the daily rollup so history stays cheap to query.
 */
class UptimeCalculator(
    private val heartbeats: HeartbeatRepository,
    private val stats: StatRepository,
) {
    suspend fun statsFor(monitorId: Long): UptimeStats {
        val now = nowMs()
        val last = heartbeats.last(monitorId)
        val day = heartbeats.uptimeSince(monitorId, now - DAY_MS)

        return UptimeStats(
            monitorId = monitorId,
            uptime24h = day.ratio,
            uptime7d = rollup(monitorId, now - 7 * DAY_MS),
            uptime30d = rollup(monitorId, now - 30 * DAY_MS),
            uptime1y = rollup(monitorId, now - 365 * DAY_MS),
            avgPing24h = day.avgPing,
            currentStatus = last?.status ?: MonitorStatus.PENDING,
            lastCheckMs = last?.timeMs,
        )
    }

    private suspend fun rollup(monitorId: Long, sinceMs: Long): Double {
        val (ratio, _) = stats.aggregateSince(monitorId, startOfDay(sinceMs))
        return ratio
    }

    private fun startOfDay(timeMs: Long): Long = timeMs - (timeMs % DAY_MS)

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

fun Double.formatUptime(): String = "${(this * 100).let { kotlin.math.round(it * 100) / 100 }}%"
