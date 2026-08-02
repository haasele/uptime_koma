package dev.haasele.koma.shared

import dev.haasele.koma.shared.data.JvmDatabaseDriverFactory
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.notify.NoopLocalNotifier
import dev.haasele.koma.shared.remote.RemoteClient
import dev.haasele.koma.shared.remote.RemoteCommand
import dev.haasele.koma.shared.remote.RemotePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** End-to-end coverage of the WebSocket remote protocol a phone uses against a desktop engine. */
class RemoteProtocolTest {

    private lateinit var core: KomaCore
    private var port = 0
    private val token = "remote-test-token"

    @BeforeTest
    fun setUp() = runBlocking {
        port = ServerSocket(0).use { it.localPort }
        val databaseFile = Files.createTempFile("koma-remote", ".db").toFile().also { it.delete() }
        core = KomaCore.create(
            driverFactory = JvmDatabaseDriverFactory(databaseFile),
            parentContext = Dispatchers.Default,
            localNotifier = NoopLocalNotifier,
        )
        core.settings.save(
            core.settings.get().copy(
                embeddedServerEnabled = true,
                embeddedServerPort = port,
                remoteAccessEnabled = true,
                remoteAccessToken = token,
            ),
        )
        core.embeddedServer.start(port)
        awaitHealth()
    }

    @AfterTest
    fun tearDown() = runBlocking { core.shutdown() }

    private suspend fun awaitHealth() {
        repeat(40) {
            val ok = runCatching {
                java.net.URI("http://127.0.0.1:$port/api/health").toURL().openConnection()
                    .getInputStream().use { it.readBytes() }
            }.isSuccess
            if (ok) return
            delay(100)
        }
    }

    @Test
    fun `remote client authenticates and lists monitors`() = runBlocking {
        core.monitors.save(
            Monitor(
                name = "remote-target",
                type = MonitorType.HTTP,
                active = false,
                config = MonitorConfig(url = "https://example.com"),
            ),
        )

        val client = RemoteClient(this)
        try {
            val serverName = client.connect("http://127.0.0.1:$port", token)
            assertTrue(serverName.isNotBlank())

            val monitors = client.requireResponse<RemotePayload.Monitors>(RemoteCommand.ListMonitors)
            assertEquals(1, monitors.monitors.size)
            assertEquals("remote-target", monitors.monitors.first().name)

            val pause = client.request(
                RemoteCommand.SetMonitorActive(monitors.monitors.first().id, false),
            )
            assertIs<RemotePayload.Ok>(pause)
            Unit
        } finally {
            client.disconnect()
            client.close()
        }
    }

    @Test
    fun `wrong token is rejected`() = runBlocking {
        val client = RemoteClient(this)
        try {
            val error = runCatching { client.connect("http://127.0.0.1:$port", "wrong-token") }.exceptionOrNull()
            assertTrue(error != null, "expected authentication to fail")
            Unit
        } finally {
            client.close()
        }
    }
}
