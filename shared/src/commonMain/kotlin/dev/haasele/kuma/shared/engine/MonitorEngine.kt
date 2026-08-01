package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.core.KomaJson
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.data.HeartbeatRepository
import dev.haasele.koma.shared.data.InfrastructureRepository
import dev.haasele.koma.shared.data.MaintenanceRepository
import dev.haasele.koma.shared.data.MonitorRepository
import dev.haasele.koma.shared.data.SettingsRepository
import dev.haasele.koma.shared.data.StatRepository
import dev.haasele.koma.shared.domain.CertificateInfo
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.engine.executors.DnsExecutor
import dev.haasele.koma.shared.engine.executors.DockerExecutor
import dev.haasele.koma.shared.engine.executors.GroupExecutor
import dev.haasele.koma.shared.engine.executors.HttpExecutor
import dev.haasele.koma.shared.engine.executors.ManualExecutor
import dev.haasele.koma.shared.engine.executors.MongoExecutor
import dev.haasele.koma.shared.engine.executors.MqttExecutor
import dev.haasele.koma.shared.engine.executors.MySqlExecutor
import dev.haasele.koma.shared.engine.executors.PingExecutor
import dev.haasele.koma.shared.engine.executors.PostgresExecutor
import dev.haasele.koma.shared.engine.executors.PushExecutor
import dev.haasele.koma.shared.engine.executors.RabbitMqExecutor
import dev.haasele.koma.shared.engine.executors.RadiusExecutor
import dev.haasele.koma.shared.engine.executors.RedisExecutor
import dev.haasele.koma.shared.engine.executors.SnmpExecutor
import dev.haasele.koma.shared.engine.executors.SteamExecutor
import dev.haasele.koma.shared.engine.executors.TcpPortExecutor
import dev.haasele.koma.shared.engine.executors.WebSocketExecutor
import dev.haasele.koma.shared.net.HttpClientProvider
import dev.haasele.koma.shared.notify.NotificationDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

sealed interface EngineEvent {
    data class BeatRecorded(val monitorId: Long, val heartbeat: Heartbeat) : EngineEvent
    data class StatusChanged(val monitorId: Long, val from: MonitorStatus?, val to: MonitorStatus) : EngineEvent
    data class RunningChanged(val running: Boolean) : EngineEvent
}

private data class MonitorRuntimeState(
    var lastStatus: MonitorStatus? = null,
    var retries: Int = 0,
    var downCount: Int = 0,
    var lastBeatMs: Long? = null,
    var lastCertificateWarningDay: Long = 0,
)

/**
 * Owns the polling loops. One coroutine per active monitor mirrors Uptime Koma's per monitor
 * timer, which keeps a slow check from delaying every other monitor.
 */
class MonitorEngine(
    private val monitors: MonitorRepository,
    private val heartbeats: HeartbeatRepository,
    private val maintenances: MaintenanceRepository,
    private val infrastructure: InfrastructureRepository,
    private val stats: StatRepository,
    private val settings: SettingsRepository,
    private val notifier: NotificationDispatcher,
    private val scope: CoroutineScope,
) {
    private val httpClients = HttpClientProvider()
    private val executors: Map<MonitorType, CheckExecutor> = buildMap {
        listOf(
            HttpExecutor(MonitorType.HTTP),
            HttpExecutor(MonitorType.KEYWORD),
            HttpExecutor(MonitorType.JSON_QUERY),
            TcpPortExecutor(),
            PingExecutor(),
            DnsExecutor(),
            PushExecutor(),
            GroupExecutor(),
            WebSocketExecutor(),
            MqttExecutor(),
            DockerExecutor(),
            SteamExecutor(),
            RedisExecutor(),
            PostgresExecutor(),
            MySqlExecutor(),
            MongoExecutor(),
            RadiusExecutor(),
            SnmpExecutor(),
            RabbitMqExecutor(),
            ManualExecutor(),
        ).forEach { put(it.type, it) }
    }

    private val jobs = mutableMapOf<Long, Job>()
    private val runtimeStates = mutableMapOf<Long, MonitorRuntimeState>()
    private val pushTimestamps = mutableMapOf<Long, Long>()
    private val mutex = Mutex()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val context = CheckContext(
        httpClients = httpClients,
        proxyProvider = { id -> id?.let { infrastructure.getProxy(it) }?.takeIf { it.active } },
        dockerHostProvider = { id -> id?.let { infrastructure.getDockerHost(it) } },
        childrenProvider = { parentId -> monitors.getChildren(parentId) },
        lastHeartbeatProvider = { monitorId -> heartbeats.last(monitorId) },
        lastPushProvider = { monitorId -> pushTimestamps[monitorId] ?: loadPersistedPush(monitorId) },
    )

    suspend fun start() {
        if (_running.value) return
        _running.value = true
        _events.emit(EngineEvent.RunningChanged(true))
        monitors.getActive().forEach { startMonitor(it) }
        scope.launch { retentionLoop() }
    }

    suspend fun stop() {
        mutex.withLock {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        httpClients.close()
        _running.value = false
        _events.emit(EngineEvent.RunningChanged(false))
    }

    /** Applies an edit, a pause or a resume without restarting the whole engine. */
    suspend fun syncMonitor(monitorId: Long) {
        val monitor = monitors.getById(monitorId)
        mutex.withLock {
            jobs.remove(monitorId)?.cancel()
            runtimeStates.remove(monitorId)
        }
        if (monitor != null && monitor.active && _running.value) startMonitor(monitor)
    }

    /** Sets the status of a manual monitor; the scheduled beat then simply keeps reporting it. */
    suspend fun setManualStatus(monitorId: Long, status: MonitorStatus, message: String) {
        val monitor = monitors.getById(monitorId) ?: return
        recordBeat(monitor, CheckResult(status, message.ifBlank { "Set manually" }))
    }

    suspend fun removeMonitor(monitorId: Long) {
        mutex.withLock {
            jobs.remove(monitorId)?.cancel()
            runtimeStates.remove(monitorId)
            pushTimestamps.remove(monitorId)
        }
    }

    private suspend fun startMonitor(monitor: Monitor) {
        val job = scope.launch {
            // Spread the first beat so restarting the app does not fire every check at once.
            delay(Random.nextLong(0, 1_500))
            while (isActive) {
                val current = monitors.getById(monitor.id)
                if (current == null || !current.active) break
                val nextDelaySeconds = try {
                    beat(current)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    recordBeat(current, CheckResult.down(error.message ?: error::class.simpleName ?: "Check failed"))
                }
                delay(nextDelaySeconds * 1000L)
            }
        }
        mutex.withLock { jobs[monitor.id] = job }
    }

    /** Runs one check and returns the number of seconds until the next one. */
    private suspend fun beat(monitor: Monitor): Long {
        if (isUnderMaintenance(monitor)) {
            recordMaintenanceBeat(monitor)
            return monitor.intervalSeconds.toLong()
        }

        val executor = executors[monitor.type]
            ?: return recordBeat(monitor, CheckResult.down("No executor for ${monitor.type.label}"))

        val timeoutMs = monitor.timeoutSeconds * 1000L + GRACE_MS
        val result = try {
            withTimeout(timeoutMs) { executor.check(monitor, context) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: UnsupportedOnPlatformException) {
            CheckResult.down(error.message ?: "Not supported on this platform")
        } catch (error: Throwable) {
            CheckResult.down(describe(error))
        }

        return recordBeat(monitor, result)
    }

    private suspend fun recordBeat(monitor: Monitor, rawResult: CheckResult): Long {
        val result = applyUpsideDown(monitor, rawResult)
        val state = runtimeStates.getOrPut(monitor.id) {
            MonitorRuntimeState(lastStatus = heartbeats.last(monitor.id)?.status)
        }

        val previousStatus = state.lastStatus
        val effectiveStatus = when {
            result.status != MonitorStatus.DOWN -> {
                state.retries = 0
                result.status
            }
            monitor.maxRetries > 0 && state.retries < monitor.maxRetries -> {
                state.retries++
                MonitorStatus.PENDING
            }
            else -> MonitorStatus.DOWN
        }

        val now = nowMs()
        val important = previousStatus == null || previousStatus != effectiveStatus
        val duration = state.lastBeatMs?.let { (now - it) / 1000 } ?: 0L

        if (effectiveStatus == MonitorStatus.DOWN) state.downCount++ else state.downCount = 0

        val heartbeat = Heartbeat(
            monitorId = monitor.id,
            status = effectiveStatus,
            message = result.message,
            pingMs = result.pingMs,
            important = important,
            timeMs = now,
            durationSeconds = duration,
            retries = state.retries,
            downCount = state.downCount,
        )
        heartbeats.insert(heartbeat)

        state.lastStatus = effectiveStatus
        state.lastBeatMs = now

        stats.record(
            monitorId = monitor.id,
            dayMs = startOfDay(now),
            up = if (effectiveStatus == MonitorStatus.UP) 1 else 0,
            down = if (effectiveStatus == MonitorStatus.DOWN) 1 else 0,
            maintenance = 0,
            pingMs = result.pingMs,
        )

        _events.emit(EngineEvent.BeatRecorded(monitor.id, heartbeat))
        if (important) {
            _events.emit(EngineEvent.StatusChanged(monitor.id, previousStatus, effectiveStatus))
            if (effectiveStatus != MonitorStatus.PENDING) notifier.notifyStatusChange(monitor, heartbeat)
        } else if (shouldResend(monitor, state, effectiveStatus)) {
            state.downCount = 0
            notifier.notifyStatusChange(monitor, heartbeat)
        }

        result.certificate?.let { certificate ->
            settings.putRaw(
                certificateSettingKey(monitor.id),
                KomaJson.encodeToString(CertificateInfo.serializer(), certificate),
            )
            maybeWarnAboutCertificate(monitor, certificate, state)
        }

        return if (effectiveStatus == MonitorStatus.PENDING) {
            monitor.retryIntervalSeconds.toLong()
        } else {
            monitor.intervalSeconds.toLong()
        }
    }

    private fun shouldResend(monitor: Monitor, state: MonitorRuntimeState, status: MonitorStatus): Boolean =
        status == MonitorStatus.DOWN &&
            monitor.resendIntervalBeats > 0 &&
            state.downCount >= monitor.resendIntervalBeats

    /** Last certificate seen for a monitor, kept so the detail screen can show it without a probe. */
    suspend fun certificateFor(monitorId: Long): CertificateInfo? =
        settings.getRaw(certificateSettingKey(monitorId))?.let { stored ->
            runCatching { KomaJson.decodeFromString(CertificateInfo.serializer(), stored) }.getOrNull()
        }

    private suspend fun maybeWarnAboutCertificate(
        monitor: Monitor,
        certificate: CertificateInfo,
        state: MonitorRuntimeState,
    ) {
        if (!monitor.config.certificateExpiryNotification) return
        val today = startOfDay(nowMs())
        if (state.lastCertificateWarningDay == today) return
        val threshold = monitor.config.certificateExpiryDays.filter { certificate.daysRemaining <= it }.maxOrNull()
            ?: return
        state.lastCertificateWarningDay = today
        notifier.notifyCertificateExpiry(monitor, certificate, threshold)
    }

    private suspend fun recordMaintenanceBeat(monitor: Monitor) {
        val state = runtimeStates.getOrPut(monitor.id) { MonitorRuntimeState() }
        val now = nowMs()
        val important = state.lastStatus != MonitorStatus.MAINTENANCE
        val heartbeat = Heartbeat(
            monitorId = monitor.id,
            status = MonitorStatus.MAINTENANCE,
            message = "Under maintenance",
            pingMs = null,
            important = important,
            timeMs = now,
            durationSeconds = state.lastBeatMs?.let { (now - it) / 1000 } ?: 0L,
        )
        heartbeats.insert(heartbeat)
        state.lastStatus = MonitorStatus.MAINTENANCE
        state.lastBeatMs = now
        state.retries = 0
        stats.record(monitor.id, startOfDay(now), up = 0, down = 0, maintenance = 1, pingMs = null)
        _events.emit(EngineEvent.BeatRecorded(monitor.id, heartbeat))
    }

    private suspend fun isUnderMaintenance(monitor: Monitor): Boolean {
        val now = nowMs()
        return maintenances.getActive().any { maintenance ->
            monitor.id in maintenance.monitorIds && MaintenanceEvaluator.isUnderMaintenance(maintenance, now)
        }
    }

    private fun applyUpsideDown(monitor: Monitor, result: CheckResult): CheckResult {
        if (!monitor.upsideDown) return result
        return when (result.status) {
            MonitorStatus.UP -> result.copy(
                status = MonitorStatus.DOWN,
                message = "Upside down mode: expected a failure but got \"${result.message}\"",
            )
            MonitorStatus.DOWN -> result.copy(
                status = MonitorStatus.UP,
                message = "Upside down mode: expected failure occurred (${result.message})",
            )
            else -> result
        }
    }

    /** Called by the push endpoint; the scheduled beat then only validates the freshness. */
    suspend fun recordPush(monitor: Monitor, status: MonitorStatus, message: String, pingMs: Long?) {
        val now = nowMs()
        pushTimestamps[monitor.id] = now
        settings.putRaw(pushSettingKey(monitor.id), now.toString())
        if (status == MonitorStatus.DOWN) {
            recordBeat(monitor, CheckResult(MonitorStatus.DOWN, message, pingMs))
        } else {
            recordBeat(monitor, CheckResult(MonitorStatus.UP, message, pingMs))
        }
    }

    private suspend fun loadPersistedPush(monitorId: Long): Long? =
        settings.getRaw(pushSettingKey(monitorId))?.toLongOrNull()?.also { pushTimestamps[monitorId] = it }

    /** Runs a single check on demand without touching the stored history. */
    suspend fun testCheck(monitor: Monitor): CheckResult {
        val executor = executors[monitor.type] ?: return CheckResult.down("No executor for ${monitor.type.label}")
        return try {
            withTimeout(monitor.timeoutSeconds * 1000L + GRACE_MS) { executor.check(monitor, context) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            CheckResult.down(describe(error))
        }
    }

    private suspend fun retentionLoop() {
        while (scope.isActive && _running.value) {
            val keepDays = settings.get().keepHeartbeatDays
            if (keepDays > 0) heartbeats.prune(nowMs() - keepDays * 86_400_000L)
            delay(RETENTION_INTERVAL_MS)
        }
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "Check failed"

    private fun startOfDay(timeMs: Long): Long = timeMs - (timeMs % 86_400_000L)

    private fun pushSettingKey(monitorId: Long) = "push_last_$monitorId"

    private fun certificateSettingKey(monitorId: Long) = "cert_info_$monitorId"

    private companion object {
        const val GRACE_MS = 5_000L
        const val RETENTION_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
