package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.DockerHost
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.ProxyServer
import dev.haasele.koma.shared.net.HttpClientProvider

/** Everything an executor may need that is owned by the engine rather than the monitor. */
class CheckContext(
    val httpClients: HttpClientProvider,
    val proxyProvider: suspend (Long?) -> ProxyServer?,
    val dockerHostProvider: suspend (Long?) -> DockerHost?,
    val childrenProvider: suspend (Long) -> List<Monitor>,
    val lastHeartbeatProvider: suspend (Long) -> Heartbeat?,
    /** Timestamp of the most recent inbound push for a monitor, if one ever arrived. */
    val lastPushProvider: suspend (Long) -> Long?,
)

interface CheckExecutor {
    val type: MonitorType

    /** Implementations may throw; the engine converts failures into a DOWN heartbeat. */
    suspend fun check(monitor: Monitor, context: CheckContext): CheckResult
}

/** Signals a check that cannot run on the current platform, e.g. Docker on iOS. */
class UnsupportedOnPlatformException(message: String) : Exception(message)
