package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.Maintenance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MaintenanceRepository(private val db: KomaDatabase) {

    private val queries get() = db.maintenanceQueries

    fun observeAll(): Flow<List<Maintenance>> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { hydrate(it.toDomain()) } }

    suspend fun getAll(): List<Maintenance> = withContext(ioDispatcher) {
        queries.selectAll().executeAsList().map { hydrate(it.toDomain()) }
    }

    suspend fun getActive(): List<Maintenance> = withContext(ioDispatcher) {
        queries.selectActive().executeAsList().map { hydrate(it.toDomain()) }
    }

    suspend fun getById(id: Long): Maintenance? = withContext(ioDispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.let { hydrate(it.toDomain()) }
    }

    suspend fun getForStatusPage(pageId: Long): List<Maintenance> = withContext(ioDispatcher) {
        queries.selectForStatusPage(pageId).executeAsList().map { hydrate(it.toDomain()) }
    }

    private fun hydrate(maintenance: Maintenance): Maintenance = maintenance.copy(
        monitorIds = queries.selectMonitorIds(maintenance.id).executeAsList(),
        statusPageIds = queries.selectStatusPageIds(maintenance.id).executeAsList(),
    )

    suspend fun save(maintenance: Maintenance): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            val id = if (maintenance.id == 0L) {
                queries.insertMaintenance(
                    title = maintenance.title,
                    description = maintenance.description,
                    strategy = maintenance.strategy.id,
                    active = maintenance.active,
                    manual_active = maintenance.manualActive,
                    timezone = maintenance.timezone,
                    start_ms = maintenance.startMs,
                    end_ms = maintenance.endMs,
                    start_time = maintenance.startTime,
                    end_time = maintenance.endTime,
                    weekdays = encodeIntList(maintenance.weekdays),
                    days_of_month = encodeIntList(maintenance.daysOfMonth),
                    cron = maintenance.cron,
                    duration_minutes = maintenance.durationMinutes?.toLong(),
                    interval_day = maintenance.intervalDay?.toLong(),
                    created_at = nowMs(),
                )
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateMaintenance(
                    title = maintenance.title,
                    description = maintenance.description,
                    strategy = maintenance.strategy.id,
                    active = maintenance.active,
                    manual_active = maintenance.manualActive,
                    timezone = maintenance.timezone,
                    start_ms = maintenance.startMs,
                    end_ms = maintenance.endMs,
                    start_time = maintenance.startTime,
                    end_time = maintenance.endTime,
                    weekdays = encodeIntList(maintenance.weekdays),
                    days_of_month = encodeIntList(maintenance.daysOfMonth),
                    cron = maintenance.cron,
                    duration_minutes = maintenance.durationMinutes?.toLong(),
                    interval_day = maintenance.intervalDay?.toLong(),
                    id = maintenance.id,
                )
                maintenance.id
            }

            queries.unlinkMonitors(id)
            maintenance.monitorIds.forEach { queries.linkMonitor(id, it) }
            queries.unlinkStatusPages(id)
            maintenance.statusPageIds.forEach { queries.linkStatusPage(id, it) }
            id
        }
    }

    suspend fun setManualActive(id: Long, active: Boolean) = withContext(ioDispatcher) {
        queries.setManualActive(active, id)
    }

    suspend fun setActive(id: Long, active: Boolean) = withContext(ioDispatcher) {
        queries.setActive(active, id)
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        db.transaction {
            queries.unlinkMonitors(id)
            queries.unlinkStatusPages(id)
            queries.deleteMaintenance(id)
        }
    }
}
