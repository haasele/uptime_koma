package dev.haasele.koma.shared.engine.executors

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.engine.CheckContext
import dev.haasele.koma.shared.engine.CheckExecutor
import dev.haasele.koma.shared.engine.UnsupportedOnPlatformException
import dev.haasele.koma.shared.net.DockerClient

class DockerExecutor : CheckExecutor {
    override val type = MonitorType.DOCKER

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val host = context.dockerHostProvider(monitor.config.dockerHostId)
            ?: return CheckResult.down("No Docker host configured")

        val start = nowMs()
        val state = try {
            DockerClient.inspect(host, monitor.config.dockerContainer, monitor.timeoutSeconds * 1000L)
        } catch (error: UnsupportedOperationException) {
            throw UnsupportedOnPlatformException(error.message ?: "Docker is unavailable here")
        }
        val elapsed = nowMs() - start

        val unhealthy = state.health != null && state.health != "healthy" && state.health != "starting"
        return when {
            !state.running -> CheckResult.down("Container is ${state.status}", elapsed)
            unhealthy -> CheckResult.down("Container is running but reports ${state.health}", elapsed)
            else -> CheckResult.up("Container is ${state.status}" + (state.health?.let { " ($it)" } ?: ""), elapsed)
        }
    }
}
