package dev.haasele.koma.shared

import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.domain.Tag
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigBackupTest {

    @Test
    fun `export and import round trip remaps references`() = runBlocking {
        val source = tempCore("koma-backup-src")
        val target = tempCore("koma-backup-dst")
        try {
            val tagId = source.tags.save(Tag(name = "prod", color = "#10B981"))
            val channelId = source.notifications.save(
                NotificationChannel(name = "ops", provider = "webhook", config = mapOf("url" to "https://example.com")),
            )
            source.monitors.save(
                Monitor(
                    name = "api",
                    type = MonitorType.HTTP,
                    active = false,
                    config = MonitorConfig(url = "https://example.com/health"),
                    notificationIds = listOf(channelId),
                    tags = listOf(dev.haasele.koma.shared.domain.TagAssignment(tagId, "prod", "#10B981")),
                ),
            )

            val json = source.backup.exportJson()
            val report = target.backup.importJson(json)

            assertEquals(1, report.monitors)
            assertEquals(1, report.notifications)
            assertEquals(1, report.tags)

            val imported = target.monitors.getAll().single()
            assertEquals("api", imported.name)
            assertEquals(1, imported.notificationIds.size)
            assertEquals(1, imported.tags.size)
            assertEquals("prod", imported.tags.single().name)
            assertTrue(imported.notificationIds.single() != channelId || target.notifications.getAll().size == 1)
        } finally {
            source.shutdown()
            target.shutdown()
        }
    }

    private fun tempCore(prefix: String): KomaCore {
        val databaseFile = Files.createTempFile(prefix, ".db").toFile().also { it.delete() }
        return KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = NoopLocalNotifier,
        )
    }
}
