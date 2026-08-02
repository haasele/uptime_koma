package dev.haasele.koma.shared.engine.executors

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.crypto.base64
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.engine.CheckContext
import dev.haasele.koma.shared.engine.CheckExecutor
import dev.haasele.koma.shared.net.HttpClientSpec
import dev.haasele.koma.shared.net.RadiusClient
import dev.haasele.koma.shared.net.RadiusReply
import dev.haasele.koma.shared.net.SnmpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

class RadiusExecutor : CheckExecutor {
    override val type = MonitorType.RADIUS

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val reply = RadiusClient.authenticate(
            host = config.hostname,
            port = if (config.port > 0) config.port else 1812,
            secret = config.radiusSecret,
            username = config.databaseUser,
            password = config.databasePassword,
            calledStationId = config.radiusCalledStationId,
            callingStationId = config.radiusCallingStationId,
            timeoutMs = monitor.timeoutSeconds * 1000L,
        )
        val elapsed = nowMs() - start

        return when (reply) {
            RadiusReply.ACCEPT, RadiusReply.CHALLENGE -> CheckResult.up(reply.label, elapsed)
            RadiusReply.REJECT -> CheckResult.down("Server rejected the credentials", elapsed)
            RadiusReply.UNKNOWN -> CheckResult.down("Unexpected RADIUS response", elapsed)
        }
    }
}

class SnmpExecutor : CheckExecutor {
    override val type = MonitorType.SNMP

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val value = SnmpClient.get(
            host = config.hostname,
            port = if (config.port > 0) config.port else 161,
            community = config.snmpCommunity.ifBlank { "public" },
            oid = config.snmpOid,
            version2c = config.snmpVersion2c,
            timeoutMs = monitor.timeoutSeconds * 1000L,
        )
        val elapsed = nowMs() - start

        val expected = config.snmpExpectedValue
        return when {
            expected.isBlank() -> CheckResult.up("${config.snmpOid} = $value", elapsed)
            value.trim() == expected.trim() -> CheckResult.up("${config.snmpOid} = $value", elapsed)
            else -> CheckResult.down("Expected \"$expected\" but got \"$value\"", elapsed)
        }
    }
}

/**
 * Talks to the RabbitMQ management plugin. The health check endpoint answers with a 503 and a
 * `reason` when a node is unhealthy, so the status code alone already carries the verdict.
 */
class RabbitMqExecutor : CheckExecutor {
    override val type = MonitorType.RABBITMQ

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val client = context.httpClients.get(
            HttpClientSpec(
                ignoreTls = config.ignoreTls,
                timeoutMs = monitor.timeoutSeconds * 1000L,
                proxy = context.proxyProvider(monitor.proxyId),
            ),
        )
        val base = config.url.trimEnd('/')
        val start = nowMs()
        val response = client.get("$base/api/health/checks/alarms") {
            header(
                "Authorization",
                "Basic " + "${config.databaseUser}:${config.databasePassword}".base64(),
            )
        }
        val elapsed = nowMs() - start
        val body = runCatching { response.bodyAsText() }.getOrDefault("")

        return when (response.status.value) {
            200 -> CheckResult.up("No alarms in effect", elapsed)
            401, 403 -> CheckResult.down("Management API rejected the credentials", elapsed)
            else -> CheckResult.down("Node unhealthy (${response.status.value}): ${body.take(120)}", elapsed)
        }
    }
}

/**
 * A manual monitor has no probe: it keeps whatever status an operator last set, which is how
 * teams track things the engine cannot reach, such as a third party service.
 */
class ManualExecutor : CheckExecutor {
    override val type = MonitorType.MANUAL

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val last = context.lastHeartbeatProvider(monitor.id)
            ?: return CheckResult(MonitorStatus.PENDING, "Waiting for a manual status")
        return CheckResult(last.status, last.message)
    }
}
