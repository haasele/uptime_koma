package dev.haasele.koma.shared

import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.StatusPageGroup
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real engine against a real local HTTP server, which is the only way to catch
 * regressions in the executor plumbing that unit tests with fakes would hide.
 */
class EngineIntegrationTest {

    private lateinit var core: KomaCore
    private var port: Int = 0
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    @BeforeTest
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        server = embeddedServer(CIO, port = port) {
            routing {
                get("/ok") { call.respondText("service is healthy") }
                get("/json") { call.respondText("""{"status":"ok","count":3}""") }
                get("/fail") {
                    call.respondText("boom", status = io.ktor.http.HttpStatusCode.InternalServerError)
                }
            }
        }.also { it.start(wait = false) }

        val databaseFile = Files.createTempFile("koma-test", ".db").toFile().also { it.delete() }
        core = KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = NoopLocalNotifier,
        )
    }

    @AfterTest
    fun tearDown() {
        runBlocking { core.shutdown() }
        server?.stop(100, 500)
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    @Test
    fun `http monitor reports up for a successful response`() = runBlocking {
        val monitor = Monitor(
            name = "local",
            type = MonitorType.HTTP,
            config = MonitorConfig(url = url("/ok")),
        )
        val result = core.engine.testCheck(monitor)
        assertEquals(MonitorStatus.UP, result.status, result.message)
        assertTrue((result.pingMs ?: -1) >= 0)
    }

    @Test
    fun `http monitor reports down for an unaccepted status code`() = runBlocking {
        val monitor = Monitor(
            name = "local",
            type = MonitorType.HTTP,
            config = MonitorConfig(url = url("/fail")),
        )
        assertEquals(MonitorStatus.DOWN, core.engine.testCheck(monitor).status)
    }

    @Test
    fun `keyword monitor matches the response body`() = runBlocking {
        val present = Monitor(
            name = "keyword",
            type = MonitorType.KEYWORD,
            config = MonitorConfig(url = url("/ok"), keyword = "healthy"),
        )
        val missing = present.copy(config = present.config.copy(keyword = "unhealthy"))

        assertEquals(MonitorStatus.UP, core.engine.testCheck(present).status)
        assertEquals(MonitorStatus.DOWN, core.engine.testCheck(missing).status)
    }

    @Test
    fun `json query monitor compares the extracted value`() = runBlocking {
        val monitor = Monitor(
            name = "json",
            type = MonitorType.JSON_QUERY,
            config = MonitorConfig(url = url("/json"), jsonPath = "$.status", expectedValue = "ok"),
        )
        assertEquals(MonitorStatus.UP, core.engine.testCheck(monitor).status)

        val mismatch = monitor.copy(config = monitor.config.copy(expectedValue = "degraded"))
        assertEquals(MonitorStatus.DOWN, core.engine.testCheck(mismatch).status)
    }

    @Test
    fun `tcp monitor connects to an open port`() = runBlocking {
        val monitor = Monitor(
            name = "port",
            type = MonitorType.PORT,
            config = MonitorConfig(hostname = "127.0.0.1", port = port),
        )
        assertEquals(MonitorStatus.UP, core.engine.testCheck(monitor).status)

        val closed = monitor.copy(config = monitor.config.copy(port = 1))
        assertEquals(MonitorStatus.DOWN, core.engine.testCheck(closed).status)
    }

    @Test
    fun `upside down mode inverts the outcome`() = runBlocking {
        val id = core.monitors.save(
            Monitor(
                name = "inverted",
                type = MonitorType.HTTP,
                upsideDown = true,
                active = false,
                config = MonitorConfig(url = url("/ok")),
            ),
        )
        val monitor = core.monitors.getById(id)!!
        core.engine.recordPush(monitor, MonitorStatus.UP, "pushed", 12)

        // recordPush goes through the same pipeline as a scheduled beat, so upside down applies.
        assertEquals(MonitorStatus.DOWN, core.heartbeats.last(id)?.status)
    }

    @Test
    fun `push monitor stays up while pushes keep arriving`() = runBlocking {
        val id = core.monitors.save(
            Monitor(
                name = "push",
                type = MonitorType.PUSH,
                active = false,
                pushToken = "test-token",
                intervalSeconds = 60,
            ),
        )
        val monitor = core.monitors.getById(id)!!

        assertEquals("push", core.monitors.getByPushToken("test-token")?.name)

        core.engine.recordPush(monitor, MonitorStatus.UP, "alive", 5)
        assertEquals(MonitorStatus.UP, core.heartbeats.last(id)?.status)
        assertEquals(MonitorStatus.UP, core.engine.testCheck(monitor).status)
    }

    @Test
    fun `group monitor aggregates its children`() = runBlocking {
        val groupId = core.monitors.save(Monitor(name = "group", type = MonitorType.GROUP, active = false))
        val childId = core.monitors.save(
            Monitor(
                name = "child",
                type = MonitorType.HTTP,
                parentId = groupId,
                config = MonitorConfig(url = url("/ok")),
            ),
        )

        val child = core.monitors.getById(childId)!!
        core.engine.recordPush(child, MonitorStatus.DOWN, "child is down", null)

        val group = core.monitors.getById(groupId)!!
        assertEquals(MonitorStatus.DOWN, core.engine.testCheck(group).status)
    }

    @Test
    fun `statistics and status page views build from recorded beats`() = runBlocking {
        val id = core.monitors.save(
            Monitor(
                name = "tracked",
                type = MonitorType.HTTP,
                active = false,
                config = MonitorConfig(url = url("/ok")),
            ),
        )
        val monitor = core.monitors.getById(id)!!
        repeat(3) { core.engine.recordPush(monitor, MonitorStatus.UP, "ok", 20) }

        val stats = core.uptime.statsFor(id)
        assertEquals(MonitorStatus.UP, stats.currentStatus)
        assertEquals(1.0, stats.uptime24h)

        val pageId = core.statusPages.save(
            StatusPage(
                slug = "public",
                title = "Public",
                groups = listOf(StatusPageGroup(name = "Core", monitorIds = listOf(id))),
            ),
        )
        val view = core.statusPageService.viewById(pageId)!!
        assertEquals(MonitorStatus.UP, view.overall)
        assertEquals("tracked", view.groups.single().monitors.single().monitor.name)
    }
}
