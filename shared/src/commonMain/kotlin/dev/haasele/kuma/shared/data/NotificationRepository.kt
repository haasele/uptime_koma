package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.NotificationChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationRepository(private val db: KomaDatabase) {

    private val queries get() = db.notificationChannelQueries

    fun observeAll(): Flow<List<NotificationChannel>> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<NotificationChannel> = withContext(ioDispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    suspend fun getById(id: Long): NotificationChannel? = withContext(ioDispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun getDefaults(): List<NotificationChannel> = withContext(ioDispatcher) {
        queries.selectDefaults().executeAsList().map { it.toDomain() }
    }

    suspend fun getForMonitor(monitorId: Long): List<NotificationChannel> = withContext(ioDispatcher) {
        queries.selectForMonitor(monitorId).executeAsList().map { it.toDomain() }
    }

    suspend fun save(channel: NotificationChannel): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            if (channel.id == 0L) {
                queries.insertChannel(
                    name = channel.name,
                    provider = channel.provider,
                    config = encodeStringMap(channel.config),
                    active = channel.active,
                    is_default = channel.isDefault,
                    created_at = nowMs(),
                )
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateChannel(
                    name = channel.name,
                    provider = channel.provider,
                    config = encodeStringMap(channel.config),
                    active = channel.active,
                    is_default = channel.isDefault,
                    id = channel.id,
                )
                channel.id
            }
        }
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        db.transaction {
            queries.unlinkChannel(id)
            queries.deleteChannel(id)
        }
    }

    suspend fun applyDefaultsTo(monitorId: Long) = withContext(ioDispatcher) {
        db.transaction {
            queries.selectDefaults().executeAsList().forEach { queries.linkNotification(monitorId, it.id) }
        }
    }
}
