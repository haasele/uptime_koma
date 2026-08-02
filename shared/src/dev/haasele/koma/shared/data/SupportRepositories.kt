package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.crypto.Passwords
import dev.haasele.koma.shared.crypto.randomToken
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.ApiKey
import dev.haasele.koma.shared.domain.AppUser
import dev.haasele.koma.shared.domain.DockerHost
import dev.haasele.koma.shared.domain.ProxyServer
import dev.haasele.koma.shared.domain.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TagRepository(private val db: KomaDatabase) {

    private val queries get() = db.tagQueries

    fun observeAll(): Flow<List<Tag>> =
        queries.selectAllTags().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<Tag> = withContext(ioDispatcher) {
        queries.selectAllTags().executeAsList().map { it.toDomain() }
    }

    suspend fun save(tag: Tag): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            if (tag.id == 0L) {
                queries.insertTag(tag.name, tag.color)
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateTag(tag.name, tag.color, tag.id)
                tag.id
            }
        }
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        db.transaction {
            queries.deleteTagLinks(id)
            queries.deleteTag(id)
        }
    }
}

class UserRepository(private val db: KomaDatabase) {

    private val queries get() = db.userQueries

    suspend fun isInitialized(): Boolean = withContext(ioDispatcher) { queries.countUsers().executeAsOne() > 0 }

    suspend fun createUser(username: String, password: String): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            queries.insertUser(username, Passwords.hash(password), nowMs())
            queries.lastInsertedId().executeAsOne()
        }
    }

    suspend fun authenticate(username: String, password: String): AppUser? = withContext(ioDispatcher) {
        val row = queries.selectByUsername(username).executeAsOneOrNull() ?: return@withContext null
        if (!Passwords.verify(password, row.password_hash)) return@withContext null
        if (Passwords.needsRehash(row.password_hash)) {
            queries.updatePassword(Passwords.hash(password), row.id)
        }
        AppUser(id = row.id, username = row.username, twoFactorEnabled = row.twofa_enabled)
    }

    suspend fun twoFactorSecret(username: String): String? = withContext(ioDispatcher) {
        queries.selectByUsername(username).executeAsOneOrNull()?.takeIf { it.twofa_enabled }?.twofa_secret
    }

    suspend fun currentUser(): AppUser? = withContext(ioDispatcher) {
        queries.selectById(1).executeAsOneOrNull()?.let {
            AppUser(id = it.id, username = it.username, twoFactorEnabled = it.twofa_enabled)
        }
    }

    suspend fun changePassword(userId: Long, currentPassword: String, newPassword: String): Boolean =
        withContext(ioDispatcher) {
            val row = queries.selectById(userId).executeAsOneOrNull() ?: return@withContext false
            if (!Passwords.verify(currentPassword, row.password_hash)) return@withContext false
            queries.updatePassword(Passwords.hash(newPassword), userId)
            true
        }

    suspend fun changeUsername(userId: Long, username: String) = withContext(ioDispatcher) {
        queries.updateUsername(username, userId)
    }

    suspend fun setTwoFactor(userId: Long, secret: String?, enabled: Boolean) = withContext(ioDispatcher) {
        queries.updateTwoFactor(secret, enabled, userId)
    }
}

class InfrastructureRepository(private val db: KomaDatabase) {

    private val queries get() = db.infrastructureQueries

    fun observeProxies(): Flow<List<ProxyServer>> =
        queries.selectProxies().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    suspend fun getProxies(): List<ProxyServer> = withContext(ioDispatcher) {
        queries.selectProxies().executeAsList().map { it.toDomain() }
    }

    suspend fun getProxy(id: Long): ProxyServer? = withContext(ioDispatcher) {
        queries.selectProxyById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun saveProxy(proxy: ProxyServer): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            if (proxy.id == 0L) {
                queries.insertProxy(
                    protocol = proxy.protocol,
                    host = proxy.host,
                    port = proxy.port.toLong(),
                    username = proxy.username,
                    password = proxy.password,
                    active = proxy.active,
                    is_default = proxy.isDefault,
                    created_at = nowMs(),
                )
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateProxy(
                    protocol = proxy.protocol,
                    host = proxy.host,
                    port = proxy.port.toLong(),
                    username = proxy.username,
                    password = proxy.password,
                    active = proxy.active,
                    is_default = proxy.isDefault,
                    id = proxy.id,
                )
                proxy.id
            }
        }
    }

    suspend fun deleteProxy(id: Long) = withContext(ioDispatcher) { queries.deleteProxy(id) }

    fun observeDockerHosts(): Flow<List<DockerHost>> =
        queries.selectDockerHosts().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    suspend fun getDockerHosts(): List<DockerHost> = withContext(ioDispatcher) {
        queries.selectDockerHosts().executeAsList().map { it.toDomain() }
    }

    suspend fun getDockerHost(id: Long): DockerHost? = withContext(ioDispatcher) {
        queries.selectDockerHostById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun saveDockerHost(host: DockerHost): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            if (host.id == 0L) {
                queries.insertDockerHost(host.name, host.connectionType.name.lowercase(), host.daemon, nowMs())
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateDockerHost(host.name, host.connectionType.name.lowercase(), host.daemon, host.id)
                host.id
            }
        }
    }

    suspend fun deleteDockerHost(id: Long) = withContext(ioDispatcher) { queries.deleteDockerHost(id) }

    fun observeApiKeys(): Flow<List<ApiKey>> =
        queries.selectApiKeys().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    /** Returns the plaintext key exactly once; only its hash is persisted. */
    suspend fun createApiKey(name: String, expiresAt: Long?): String = withContext(ioDispatcher) {
        val secret = randomToken(40)
        val prefix = "uk_${randomToken(6)}"
        val fullKey = "${prefix}_$secret"
        queries.insertApiKey(name, Passwords.hash(secret), prefix, true, expiresAt, nowMs())
        fullKey
    }

    suspend fun verifyApiKey(fullKey: String): Boolean = withContext(ioDispatcher) {
        val separator = fullKey.lastIndexOf('_')
        if (separator <= 0) return@withContext false
        val prefix = fullKey.substring(0, separator)
        val secret = fullKey.substring(separator + 1)
        val now = nowMs()
        queries.selectApiKeys().executeAsList().any { row ->
            row.active &&
                row.prefix == prefix &&
                (row.expires_at == null || row.expires_at > now) &&
                Passwords.verify(secret, row.key_hash)
        }
    }

    suspend fun setApiKeyActive(id: Long, active: Boolean) = withContext(ioDispatcher) {
        queries.setApiKeyActive(active, id)
    }

    suspend fun deleteApiKey(id: Long) = withContext(ioDispatcher) { queries.deleteApiKey(id) }
}

class StatRepository(private val db: KomaDatabase) {

    private val queries get() = db.statQueries

    suspend fun record(monitorId: Long, dayMs: Long, up: Int, down: Int, maintenance: Int, pingMs: Long?) =
        withContext(ioDispatcher) {
            db.transaction {
                val existing = queries.selectDay(monitorId, dayMs).executeAsOneOrNull()
                val ping = pingMs?.toDouble()
                queries.insertDaily(
                    monitor_id = monitorId,
                    day_ms = dayMs,
                    up = (existing?.up ?: 0) + up,
                    down = (existing?.down ?: 0) + down,
                    maintenance = (existing?.maintenance ?: 0) + maintenance,
                    ping_sum = (existing?.ping_sum ?: 0.0) + (ping ?: 0.0),
                    ping_count = (existing?.ping_count ?: 0) + if (ping != null) 1 else 0,
                    ping_min = listOfNotNull(existing?.ping_min, ping).minOrNull(),
                    ping_max = listOfNotNull(existing?.ping_max, ping).maxOrNull(),
                )
            }
        }

    suspend fun dailySince(monitorId: Long, sinceMs: Long) = withContext(ioDispatcher) {
        queries.selectDaily(monitorId, sinceMs).executeAsList()
    }

    /**
     * Gapless daily series for charts: days without data become explicit entries so a chart
     * cannot silently compress a monitoring outage into a shorter bar run.
     */
    suspend fun dailySeries(monitorId: Long, days: Int): List<DailyUptime> = withContext(ioDispatcher) {
        val today = startOfDay(nowMs())
        val from = today - (days - 1) * DAY_MS
        val recorded = queries.selectDaily(monitorId, from).executeAsList().associateBy { it.day_ms }

        (0 until days).map { offset ->
            val day = from + offset * DAY_MS
            val row = recorded[day]
            DailyUptime(
                dayMs = day,
                up = row?.up ?: 0,
                down = row?.down ?: 0,
                maintenance = row?.maintenance ?: 0,
                avgPing = row?.let { entry ->
                    val count = entry.ping_count
                    if (count > 0) entry.ping_sum / count else null
                },
            )
        }
    }

    suspend fun aggregateSince(monitorId: Long, sinceMs: Long): Pair<Double, Double?> = withContext(ioDispatcher) {
        val row = queries.aggregateSince(monitorId, sinceMs).executeAsOne()
        val up = row.upCount ?: 0
        val down = row.downCount ?: 0
        val total = up + down
        val ratio = if (total > 0) up.toDouble() / total else 0.0
        val pingCount = row.pingCount ?: 0
        val avgPing = if (pingCount > 0) (row.pingSum ?: 0.0) / pingCount else null
        ratio to avgPing
    }

    private fun startOfDay(timeMs: Long): Long = timeMs - (timeMs % DAY_MS)

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

data class DailyUptime(
    val dayMs: Long,
    val up: Long,
    val down: Long,
    val maintenance: Long,
    val avgPing: Double?,
) {
    val hasData: Boolean get() = up + down + maintenance > 0

    val ratio: Double
        get() {
            val total = up + down
            return if (total > 0) up.toDouble() / total else 0.0
        }
}
