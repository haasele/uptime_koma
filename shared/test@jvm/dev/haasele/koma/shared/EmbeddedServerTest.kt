package dev.haasele.koma.shared

import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.StatusPageGroup
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the HTTP surface a desktop instance exposes: push receiver, metrics and status JSON. */
class EmbeddedServerTest {

    private lateinit var core: KomaCore
    private var port = 0

    @BeforeTest
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        val databaseFile = Files.createTempFile("koma-server", ".db").toFile().also { it.delete() }
        core = KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = NoopLocalNotifier,
        )
        core.embeddedServer.start(port)
    }

    @AfterTest
    fun tearDown() = runBlocking { core.shutdown() }

    private fun get(path: String): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        val code = connection.responseCode
        val body = (if (code < 400) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return code to body
    }

    private suspend fun awaitServer() {
        repeat(40) {
            if (runCatching { get("/api/health") }.getOrNull()?.first == 200) return
            delay(100)
        }
    }

    @Test
    fun `health endpoint reports the engine state`() = runBlocking {
        awaitServer()
        val (code, body) = get("/api/health")
        assertEquals(200, code)
        assertTrue(body.contains("\"ok\":true"), body)
    }

    @Test
    fun `push endpoint records a heartbeat for a known token`() = runBlocking {
        awaitServer()
        val id = core.monitors.save(
            Monitor(name = "pushed", type = MonitorType.PUSH, active = false, pushToken = "abc123"),
        )

        val (code, body) = get("/api/push/abc123?status=up&msg=from%20cron&ping=42")
        assertEquals(200, code)
        assertTrue(body.contains("\"ok\":true"), body)

        val beat = core.heartbeats.last(id)
        assertEquals(MonitorStatus.UP, beat?.status)
        assertEquals(42L, beat?.pingMs)

        assertEquals(404, get("/api/push/unknown-token").first)
    }

    @Test
    fun `metrics expose every monitor`() = runBlocking {
        awaitServer()
        core.settings.save(core.settings.get().copy(metricsEnabled = true))
        core.monitors.save(
            Monitor(
                name = "metric target",
                type = MonitorType.HTTP,
                active = false,
                config = MonitorConfig(url = "https://example.com"),
            ),
        )

        val (code, body) = get("/metrics")
        assertEquals(200, code)
        assertTrue(body.contains("monitor_status{monitor_name=\"metric target\""), body)
        assertTrue(body.contains("koma_monitor_count 1"), body)
    }

    @Test
    fun `status page json is served for published pages only`() = runBlocking {
        awaitServer()
        val monitorId = core.monitors.save(
            Monitor(
                name = "api",
                type = MonitorType.HTTP,
                active = false,
                config = MonitorConfig(url = "https://example.com"),
            ),
        )
        core.statusPages.save(
            StatusPage(
                slug = "public",
                title = "Public",
                groups = listOf(StatusPageGroup(name = "Core", monitorIds = listOf(monitorId))),
            ),
        )
        val hidden = core.statusPages.save(StatusPage(slug = "hidden", title = "Hidden", published = false))

        val (code, body) = get("/api/status-page/public")
        assertEquals(200, code)
        assertTrue(body.contains("\"title\":\"Public\""), body)

        assertEquals(404, get("/api/status-page/hidden").first)
        assertTrue(hidden > 0)
    }
}
