package dev.haasele.koma.shared.backup

import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.KomaJson
import dev.haasele.koma.shared.crypto.randomToken
import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.Tag
import kotlinx.serialization.Serializable

/**
 * Portable snapshot of the configuration a user cares about when moving between devices.
 * Heartbeats are deliberately omitted: they belong to the instance that collected them.
 * Entity ids are kept so references between monitors, tags and notifications rematch on import.
 */
@Serializable
data class ConfigBackup(
    val version: Int = VERSION,
    val monitors: List<Monitor> = emptyList(),
    val notifications: List<NotificationChannel> = emptyList(),
    val statusPages: List<StatusPage> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val maintenances: List<Maintenance> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

data class ImportReport(
    val monitors: Int,
    val notifications: Int,
    val statusPages: Int,
    val tags: Int,
    val maintenances: Int,
)

class ConfigBackupService(private val core: KomaCore) {

    suspend fun exportJson(): String {
        val backup = ConfigBackup(
            monitors = core.monitors.getAll(),
            notifications = core.notifications.getAll(),
            statusPages = core.statusPages.getAll(),
            tags = core.tags.getAll(),
            maintenances = core.maintenances.getAll(),
        )
        return KomaJson.encodeToString(ConfigBackup.serializer(), backup)
    }

    /**
     * Merges a previously exported snapshot into the local database. Existing entities are kept;
     * imported rows get fresh ids while references inside the backup are remapped.
     */
    suspend fun importJson(json: String): ImportReport {
        val backup = KomaJson.decodeFromString(ConfigBackup.serializer(), json)
        require(backup.version == ConfigBackup.VERSION) {
            "Unsupported backup version ${backup.version}"
        }

        val tagIds = mutableMapOf<Long, Long>()
        backup.tags.forEach { tag ->
            tagIds[tag.id] = core.tags.save(tag.copy(id = 0))
        }

        val notificationIds = mutableMapOf<Long, Long>()
        backup.notifications.forEach { channel ->
            notificationIds[channel.id] = core.notifications.save(channel.copy(id = 0))
        }

        val monitorIds = mutableMapOf<Long, Long>()
        backup.monitors.forEach { monitor ->
            val remapped = monitor.copy(
                id = 0,
                parentId = null,
                pushToken = monitor.pushToken?.let { randomToken(24) },
                notificationIds = monitor.notificationIds.mapNotNull { notificationIds[it] },
                tags = monitor.tags.mapNotNull { assignment ->
                    tagIds[assignment.tagId]?.let { newId -> assignment.copy(tagId = newId) }
                },
            )
            monitorIds[monitor.id] = core.monitors.save(remapped).also { core.engine.syncMonitor(it) }
        }

        // A second pass restores parent groups once every monitor has a new id.
        backup.monitors.filter { it.parentId != null }.forEach { monitor ->
            val newId = monitorIds[monitor.id] ?: return@forEach
            val newParent = monitorIds[monitor.parentId] ?: return@forEach
            val current = core.monitors.getById(newId) ?: return@forEach
            core.monitors.save(current.copy(parentId = newParent))
        }

        val pageIds = mutableMapOf<Long, Long>()
        backup.statusPages.forEach { page ->
            pageIds[page.id] = core.statusPages.save(
                page.copy(
                    id = 0,
                    slug = uniqueSlug(page.slug),
                    groups = page.groups.map { group ->
                        group.copy(
                            id = 0,
                            monitorIds = group.monitorIds.mapNotNull { monitorIds[it] },
                        )
                    },
                ),
            )
        }

        backup.maintenances.forEach { maintenance ->
            core.maintenances.save(
                maintenance.copy(
                    id = 0,
                    monitorIds = maintenance.monitorIds.mapNotNull { monitorIds[it] },
                    statusPageIds = maintenance.statusPageIds.mapNotNull { pageIds[it] },
                ),
            )
        }

        return ImportReport(
            monitors = monitorIds.size,
            notifications = notificationIds.size,
            statusPages = pageIds.size,
            tags = tagIds.size,
            maintenances = backup.maintenances.size,
        )
    }

    private suspend fun uniqueSlug(preferred: String): String {
        if (core.statusPages.getBySlug(preferred) == null) return preferred
        var attempt = 2
        while (core.statusPages.getBySlug("$preferred-$attempt") != null) attempt++
        return "$preferred-$attempt"
    }
}
