package dev.haasele.koma.shared

import dev.haasele.koma.shared.backup.ConfigBackupService
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.data.DatabaseDriverFactory
import dev.haasele.koma.shared.data.HeartbeatRepository
import dev.haasele.koma.shared.data.InfrastructureRepository
import dev.haasele.koma.shared.data.MaintenanceRepository
import dev.haasele.koma.shared.data.MonitorRepository
import dev.haasele.koma.shared.data.NotificationRepository
import dev.haasele.koma.shared.data.SettingsRepository
import dev.haasele.koma.shared.data.StatRepository
import dev.haasele.koma.shared.data.StatusPageRepository
import dev.haasele.koma.shared.data.TagRepository
import dev.haasele.koma.shared.data.UserRepository
import dev.haasele.koma.shared.data.createDatabase
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.engine.MonitorEngine
import dev.haasele.koma.shared.engine.StatusPageService
import dev.haasele.koma.shared.engine.UptimeCalculator
import dev.haasele.koma.shared.net.HttpClientProvider
import dev.haasele.koma.shared.notify.LocalNotifier
import dev.haasele.koma.shared.notify.LocalNotifierHolder
import dev.haasele.koma.shared.notify.NotificationDispatcher
import dev.haasele.koma.shared.server.EmbeddedServer
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Wires the whole backend together. The UI only ever talks to this object, which is what makes
 * the same Compose code work against a local engine today and a remote one later.
 */
class KomaCore(
    val database: KomaDatabase,
    parentContext: CoroutineContext,
    localNotifier: LocalNotifier,
) {
    private val startMutex = Mutex()
    private var started = false

    private val crashGuard = CoroutineExceptionHandler { _, throwable ->
        // Ktor CIO reports a failed listen on its accept job; never let that kill the process.
        val bindFailure = generateSequence(throwable) { it.cause }
            .any { it::class.simpleName == "BindException" }
        if (!bindFailure) throwable.printStackTrace()
    }

    val scope = CoroutineScope(parentContext + SupervisorJob() + crashGuard)

    val monitors = MonitorRepository(database)
    val heartbeats = HeartbeatRepository(database)
    val notifications = NotificationRepository(database)
    val statusPages = StatusPageRepository(database)
    val maintenances = MaintenanceRepository(database)
    val settings = SettingsRepository(database)
    val tags = TagRepository(database)
    val users = UserRepository(database)
    val infrastructure = InfrastructureRepository(database)
    val stats = StatRepository(database)

    private val httpClients = HttpClientProvider()

    val notificationDispatcher = NotificationDispatcher(notifications, httpClients)
    val uptime = UptimeCalculator(heartbeats, stats)
    val statusPageService = StatusPageService(statusPages, monitors, heartbeats, maintenances, uptime)

    val engine = MonitorEngine(
        monitors = monitors,
        heartbeats = heartbeats,
        maintenances = maintenances,
        infrastructure = infrastructure,
        stats = stats,
        settings = settings,
        notifier = notificationDispatcher,
        scope = scope,
    )

    val embeddedServer = EmbeddedServer(this)
    val backup = ConfigBackupService(this)

    /**
     * When the desktop process was started with `--http` / `--https` (or `--port` / `--nogui`),
     * the embedded server is owned by the CLI and must not also be toggled from Settings.
     */
    @Volatile
    var cliManagedServer: CliManagedServer? = null
        private set

    fun markCliManagedServer(info: CliManagedServer) {
        cliManagedServer = info
    }

    fun clearCliManagedServer() {
        cliManagedServer = null
    }

    init {
        LocalNotifierHolder.current = localNotifier
    }

    /**
     * Starts the engine and, where the platform allows it, the push and metrics endpoints.
     * Safe to call from Activity and Service — only the first caller does the work.
     */
    suspend fun start() {
        startMutex.withLock {
            if (started) return
            val configuration = settings.get()
            if (configuration.startEngineOnLaunch) engine.start()
            if (configuration.embeddedServerEnabled && Platform.supportsEmbeddedServer) {
                // Port conflicts must never abort the app; push/metrics simply stay offline.
                embeddedServer.start(configuration.embeddedServerPort)
            }
            started = true
        }
    }

    suspend fun shutdown() {
        startMutex.withLock {
            engine.stop()
            embeddedServer.stop()
            httpClients.close()
            scope.cancel()
            started = false
        }
    }

    companion object {
        fun create(
            driverFactory: DatabaseDriverFactory,
            parentContext: CoroutineContext,
            localNotifier: LocalNotifier,
        ): KomaCore = KomaCore(createDatabase(driverFactory), parentContext, localNotifier)
    }
}

/** Snapshot of an embedded server started from desktop CLI flags. */
data class CliManagedServer(
    val scheme: String,
    val port: Int,
    val hostnames: List<String>,
    val tls: Boolean,
) {
    fun publicUrls(): List<String> {
        val hosts = hostnames.ifEmpty { listOf("127.0.0.1") }
        return hosts.map { host -> "$scheme://$host:$port" }
    }
}
