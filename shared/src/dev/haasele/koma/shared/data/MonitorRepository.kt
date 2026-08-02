package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.TagAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MonitorRepository(private val db: KomaDatabase) {

    private val monitorQueries get() = db.monitorQueries
    private val tagQueries get() = db.tagQueries
    private val notificationQueries get() = db.notificationChannelQueries

    fun observeAll(): Flow<List<Monitor>> {
        val monitors = monitorQueries.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { it.toDomain() }
        }
        val tags = tagQueries.selectAllMonitorTags().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.groupBy({ it.monitor_id }) { TagAssignment(it.id, it.name, it.color, it.value_) }
        }
        val links = observeNotificationLinks()
        return combine(monitors, tags, links) { monitorList, tagMap, linkMap ->
            monitorList.map { monitor ->
                monitor.copy(
                    tags = tagMap[monitor.id].orEmpty(),
                    notificationIds = linkMap[monitor.id].orEmpty(),
                )
            }
        }
    }

    private fun observeNotificationLinks(): Flow<Map<Long, List<Long>>> =
        monitorQueries.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.associate { row ->
                row.id to notificationQueries.selectIdsForMonitor(row.id).executeAsList()
            }
        }

    suspend fun getAll(): List<Monitor> = withContext(ioDispatcher) {
        monitorQueries.selectAll().executeAsList().map { hydrate(it.toDomain()) }
    }

    suspend fun getActive(): List<Monitor> = withContext(ioDispatcher) {
        monitorQueries.selectActive().executeAsList().map { hydrate(it.toDomain()) }
    }

    suspend fun getById(id: Long): Monitor? = withContext(ioDispatcher) {
        monitorQueries.selectById(id).executeAsOneOrNull()?.let { hydrate(it.toDomain()) }
    }

    suspend fun getByPushToken(token: String): Monitor? = withContext(ioDispatcher) {
        monitorQueries.selectByPushToken(token).executeAsOneOrNull()?.let { hydrate(it.toDomain()) }
    }

    suspend fun getChildren(parentId: Long): List<Monitor> = withContext(ioDispatcher) {
        monitorQueries.selectChildren(parentId).executeAsList().map { hydrate(it.toDomain()) }
    }

    private fun hydrate(monitor: Monitor): Monitor = monitor.copy(
        tags = tagQueries.selectTagsForMonitor(monitor.id).executeAsList()
            .map { TagAssignment(it.id, it.name, it.color, it.value_) },
        notificationIds = notificationQueries.selectIdsForMonitor(monitor.id).executeAsList(),
    )

    suspend fun save(monitor: Monitor): Long = withContext(ioDispatcher) {
        val timestamp = nowMs()
        db.transactionWithResult {
            val id = if (monitor.id == 0L) {
                monitorQueries.insertMonitor(
                    name = monitor.name,
                    type = monitor.type.id,
                    active = monitor.active,
                    parent_id = monitor.parentId,
                    description = monitor.description,
                    interval_seconds = monitor.intervalSeconds.toLong(),
                    retry_interval_seconds = monitor.retryIntervalSeconds.toLong(),
                    resend_interval = monitor.resendIntervalBeats.toLong(),
                    max_retries = monitor.maxRetries.toLong(),
                    timeout_seconds = monitor.timeoutSeconds.toLong(),
                    upside_down = monitor.upsideDown,
                    push_token = monitor.pushToken,
                    proxy_id = monitor.proxyId,
                    weight = monitor.weight.toLong(),
                    config = encodeConfig(monitor.config),
                    created_at = timestamp,
                    updated_at = timestamp,
                )
                monitorQueries.lastInsertedId().executeAsOne()
            } else {
                monitorQueries.updateMonitor(
                    name = monitor.name,
                    type = monitor.type.id,
                    active = monitor.active,
                    parent_id = monitor.parentId,
                    description = monitor.description,
                    interval_seconds = monitor.intervalSeconds.toLong(),
                    retry_interval_seconds = monitor.retryIntervalSeconds.toLong(),
                    resend_interval = monitor.resendIntervalBeats.toLong(),
                    max_retries = monitor.maxRetries.toLong(),
                    timeout_seconds = monitor.timeoutSeconds.toLong(),
                    upside_down = monitor.upsideDown,
                    push_token = monitor.pushToken,
                    proxy_id = monitor.proxyId,
                    weight = monitor.weight.toLong(),
                    config = encodeConfig(monitor.config),
                    updated_at = timestamp,
                    id = monitor.id,
                )
                monitor.id
            }

            tagQueries.unlinkTagsForMonitor(id)
            monitor.tags.forEach { tagQueries.linkTag(id, it.tagId, it.value) }

            notificationQueries.unlinkForMonitor(id)
            monitor.notificationIds.forEach { notificationQueries.linkNotification(id, it) }

            id
        }
    }

    suspend fun setActive(id: Long, active: Boolean) = withContext(ioDispatcher) {
        monitorQueries.setActive(active, nowMs(), id)
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        db.transaction {
            // Children of a group are promoted to top level instead of disappearing silently.
            monitorQueries.selectChildren(id).executeAsList().forEach { child ->
                monitorQueries.updateMonitor(
                    name = child.name,
                    type = child.type,
                    active = child.active,
                    parent_id = null,
                    description = child.description,
                    interval_seconds = child.interval_seconds,
                    retry_interval_seconds = child.retry_interval_seconds,
                    resend_interval = child.resend_interval,
                    max_retries = child.max_retries,
                    timeout_seconds = child.timeout_seconds,
                    upside_down = child.upside_down,
                    push_token = child.push_token,
                    proxy_id = child.proxy_id,
                    weight = child.weight,
                    config = child.config,
                    updated_at = nowMs(),
                    id = child.id,
                )
            }
            db.heartbeatQueries.deleteByMonitor(id)
            db.statQueries.deleteByMonitor(id)
            tagQueries.unlinkTagsForMonitor(id)
            notificationQueries.unlinkForMonitor(id)
            monitorQueries.deleteMonitor(id)
        }
    }

    suspend fun cloneMonitor(id: Long): Long? = withContext(ioDispatcher) {
        val source = getById(id) ?: return@withContext null
        save(
            source.copy(
                id = 0,
                name = "${source.name} (copy)",
                pushToken = if (source.type == MonitorType.PUSH) null else source.pushToken,
            ),
        )
    }
}
