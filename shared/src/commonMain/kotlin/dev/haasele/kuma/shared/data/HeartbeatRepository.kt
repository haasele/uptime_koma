package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.Heartbeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class UptimeWindow(
    val upCount: Long,
    val downCount: Long,
    val upSeconds: Long,
    val downSeconds: Long,
    val avgPing: Double?,
) {
    /** Duration weighted when durations are recorded, otherwise a plain beat ratio. */
    val ratio: Double
        get() {
            val totalSeconds = upSeconds + downSeconds
            if (totalSeconds > 0) return upSeconds.toDouble() / totalSeconds
            val totalBeats = upCount + downCount
            return if (totalBeats > 0) upCount.toDouble() / totalBeats else 0.0
        }
}

class HeartbeatRepository(private val db: KomaDatabase) {

    private val queries get() = db.heartbeatQueries

    suspend fun insert(heartbeat: Heartbeat) = withContext(ioDispatcher) {
        queries.insertHeartbeat(
            monitor_id = heartbeat.monitorId,
            status = heartbeat.status.code.toLong(),
            msg = heartbeat.message,
            ping_ms = heartbeat.pingMs,
            important = heartbeat.important,
            time_ms = heartbeat.timeMs,
            duration_seconds = heartbeat.durationSeconds,
            retries = heartbeat.retries.toLong(),
            down_count = heartbeat.downCount.toLong(),
        )
    }

    fun observeRecent(monitorId: Long, limit: Int = 100): Flow<List<Heartbeat>> =
        queries.recentByMonitor(monitorId, limit.toLong()).asFlow().mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() }.reversed() }

    fun observeLatestPerMonitor(): Flow<Map<Long, Heartbeat>> =
        queries.latestPerMonitor().asFlow().mapToList(ioDispatcher)
            .map { rows -> rows.associate { it.monitor_id to it.toDomain() } }

    suspend fun recent(monitorId: Long, limit: Int = 100): List<Heartbeat> = withContext(ioDispatcher) {
        queries.recentByMonitor(monitorId, limit.toLong()).executeAsList().map { it.toDomain() }.reversed()
    }

    suspend fun last(monitorId: Long): Heartbeat? = withContext(ioDispatcher) {
        queries.lastByMonitor(monitorId).executeAsOneOrNull()?.toDomain()
    }

    suspend fun lastImportant(monitorId: Long): Heartbeat? = withContext(ioDispatcher) {
        queries.lastImportantByMonitor(monitorId).executeAsOneOrNull()?.toDomain()
    }

    suspend fun since(monitorId: Long, sinceMs: Long): List<Heartbeat> = withContext(ioDispatcher) {
        queries.sinceByMonitor(monitorId, sinceMs).executeAsList().map { it.toDomain() }
    }

    suspend fun important(monitorId: Long, limit: Int = 50): List<Heartbeat> = withContext(ioDispatcher) {
        queries.importantByMonitor(monitorId, limit.toLong()).executeAsList().map { it.toDomain() }
    }

    suspend fun uptimeSince(monitorId: Long, sinceMs: Long): UptimeWindow = withContext(ioDispatcher) {
        val row = queries.uptimeSince(monitorId, sinceMs).executeAsOne()
        UptimeWindow(
            upCount = row.upCount ?: 0,
            downCount = row.downCount ?: 0,
            upSeconds = row.upSeconds ?: 0,
            downSeconds = row.downSeconds ?: 0,
            avgPing = row.avgPing,
        )
    }

    suspend fun deleteForMonitor(monitorId: Long) = withContext(ioDispatcher) {
        queries.deleteByMonitor(monitorId)
    }

    suspend fun prune(olderThanMs: Long) = withContext(ioDispatcher) {
        queries.deleteOlderThan(olderThanMs)
    }

    suspend fun count(): Long = withContext(ioDispatcher) { queries.countAll().executeAsOne() }
}
