package dev.haasele.koma.shared

import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A short soak run: the engine schedules several monitors at once, keeps beating and stops
 * cleanly. It guards the scheduler against the two failure modes that only show up at runtime,
 * a monitor that never fires and a job that keeps running after `stop()`.
 */
class EngineSoakTest {

    @Test
    fun `engine beats every active monitor and stops cleanly`() = runBlocking {
        val requests = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, port = port) {
            routing {
                get("/probe") {
                    requests.incrementAndGet()
                    call.respondText("ok")
                }
            }
        }.also { it.start(wait = false) }

        val databaseFile = Files.createTempFile("koma-soak", ".db").toFile().also { it.delete() }
        val core = KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = NoopLocalNotifier,
        )

        try {
            val ids = (1..5).map { index ->
                core.monitors.save(
                    Monitor(
                        name = "probe-$index",
                        type = MonitorType.HTTP,
                        intervalSeconds = 20,
                        config = MonitorConfig(url = "http://127.0.0.1:$port/probe"),
                    ),
                )
            }

            core.engine.start()
            assertTrue(core.engine.running.value)

            withTimeout(15_000) {
                while (ids.any { core.heartbeats.last(it) == null }) delay(150)
            }

            ids.forEach { id ->
                val beat = core.heartbeats.last(id)
                assertEquals(MonitorStatus.UP, beat?.status, "monitor $id reported ${beat?.message}")
            }

            core.engine.stop()
            val afterStop = requests.get()
            delay(1_000)
            assertEquals(afterStop, requests.get(), "the engine kept polling after stop()")
        } finally {
            core.shutdown()
            server.stop(100, 500)
        }
    }
}
