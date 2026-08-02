package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.crypto.Passwords
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.Incident
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.StatusPageGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StatusPageRepository(private val db: KomaDatabase) {

    private val queries get() = db.statusPageQueries

    fun observeAll(): Flow<List<StatusPage>> =
        queries.selectAllPages().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { row -> row.toDomain().copy(groups = loadGroups(row.id)) }
        }

    suspend fun getAll(): List<StatusPage> = withContext(ioDispatcher) {
        queries.selectAllPages().executeAsList().map { it.toDomain().copy(groups = loadGroups(it.id)) }
    }

    suspend fun getById(id: Long): StatusPage? = withContext(ioDispatcher) {
        queries.selectPageById(id).executeAsOneOrNull()?.let { it.toDomain().copy(groups = loadGroups(it.id)) }
    }

    suspend fun getBySlug(slug: String): StatusPage? = withContext(ioDispatcher) {
        queries.selectPageBySlug(slug).executeAsOneOrNull()?.let { it.toDomain().copy(groups = loadGroups(it.id)) }
    }

    private fun loadGroups(pageId: Long): List<StatusPageGroup> =
        queries.selectGroups(pageId).executeAsList().map { group ->
            StatusPageGroup(
                id = group.id,
                name = group.name,
                weight = group.weight.toInt(),
                monitorIds = queries.selectGroupMonitors(group.id).executeAsList().map { it.monitor_id },
            )
        }

    suspend fun save(page: StatusPage, plainPassword: String? = null, clearPassword: Boolean = false): Long =
        withContext(ioDispatcher) {
            db.transactionWithResult {
                val existingHash = if (page.id == 0L) null else {
                    queries.selectPageById(page.id).executeAsOneOrNull()?.password_hash
                }
                val passwordHash = when {
                    clearPassword -> null
                    !plainPassword.isNullOrBlank() -> Passwords.hash(plainPassword)
                    else -> existingHash
                }

                val id = if (page.id == 0L) {
                    queries.insertPage(
                        slug = page.slug,
                        title = page.title,
                        description = page.description,
                        icon = page.icon,
                        theme = page.theme,
                        published = page.published,
                        show_tags = page.showTags,
                        show_uptime_percentage = page.showUptimePercentage,
                        show_certificate_expiry = page.showCertificateExpiry,
                        footer_text = page.footerText,
                        accent_color = page.accentColor,
                        password_hash = passwordHash,
                        created_at = nowMs(),
                    )
                    queries.lastInsertedId().executeAsOne()
                } else {
                    queries.updatePage(
                        slug = page.slug,
                        title = page.title,
                        description = page.description,
                        icon = page.icon,
                        theme = page.theme,
                        published = page.published,
                        show_tags = page.showTags,
                        show_uptime_percentage = page.showUptimePercentage,
                        show_certificate_expiry = page.showCertificateExpiry,
                        footer_text = page.footerText,
                        accent_color = page.accentColor,
                        password_hash = passwordHash,
                        id = page.id,
                    )
                    page.id
                }

                queries.selectGroups(id).executeAsList().forEach { queries.deleteGroupMonitors(it.id) }
                queries.deleteGroupsForPage(id)
                page.groups.forEachIndexed { index, group ->
                    queries.insertGroup(id, group.name, index.toLong())
                    val groupId = queries.lastInsertedId().executeAsOne()
                    group.monitorIds.forEachIndexed { monitorIndex, monitorId ->
                        queries.insertGroupMonitor(groupId, monitorId, monitorIndex.toLong())
                    }
                }
                id
            }
        }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        db.transaction {
            queries.selectGroups(id).executeAsList().forEach { queries.deleteGroupMonitors(it.id) }
            queries.deleteGroupsForPage(id)
            queries.selectIncidents(id).executeAsList().forEach { queries.deleteIncident(it.id) }
            queries.deletePage(id)
        }
    }

    suspend fun verifyPassword(slug: String, password: String): Boolean = withContext(ioDispatcher) {
        val hash = queries.selectPageBySlug(slug).executeAsOneOrNull()?.password_hash ?: return@withContext true
        Passwords.verify(password, hash)
    }

    suspend fun incidents(pageId: Long): List<Incident> = withContext(ioDispatcher) {
        queries.selectIncidents(pageId).executeAsList().map { it.toDomain() }
    }

    suspend fun pinnedIncident(pageId: Long): Incident? = withContext(ioDispatcher) {
        queries.selectPinnedIncident(pageId).executeAsOneOrNull()?.toDomain()
    }

    suspend fun postIncident(incident: Incident): Long = withContext(ioDispatcher) {
        val timestamp = nowMs()
        db.transactionWithResult {
            if (incident.pinned) queries.unpinIncidents(incident.statusPageId)
            if (incident.id == 0L) {
                queries.insertIncident(
                    status_page_id = incident.statusPageId,
                    title = incident.title,
                    content = incident.content,
                    style = incident.style.name.lowercase(),
                    pin = incident.pinned,
                    created_at = timestamp,
                    last_updated_at = timestamp,
                )
                queries.lastInsertedId().executeAsOne()
            } else {
                queries.updateIncident(
                    title = incident.title,
                    content = incident.content,
                    style = incident.style.name.lowercase(),
                    pin = incident.pinned,
                    last_updated_at = timestamp,
                    id = incident.id,
                )
                incident.id
            }
        }
    }

    suspend fun unpinIncidents(pageId: Long) = withContext(ioDispatcher) { queries.unpinIncidents(pageId) }

    suspend fun deleteIncident(id: Long) = withContext(ioDispatcher) { queries.deleteIncident(id) }
}
