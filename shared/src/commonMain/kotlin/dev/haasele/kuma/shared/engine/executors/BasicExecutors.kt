package dev.haasele.koma.shared.engine.executors

import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.ConditionEvaluator
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.engine.CheckContext
import dev.haasele.koma.shared.engine.CheckExecutor
import dev.haasele.koma.shared.net.DnsClient
import dev.haasele.koma.shared.net.IcmpPing
import dev.haasele.koma.shared.net.useTcp

class TcpPortExecutor : CheckExecutor {
    override val type = MonitorType.PORT

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val start = nowMs()
        useTcp(monitor.config.hostname, monitor.config.port, monitor.timeoutSeconds * 1000L) { }
        val elapsed = nowMs() - start
        return CheckResult.up("Port ${monitor.config.port} is open", elapsed)
    }
}

class PingExecutor : CheckExecutor {
    override val type = MonitorType.PING

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val outcome = IcmpPing.ping(
            host = monitor.config.hostname,
            timeoutMs = monitor.timeoutSeconds * 1000L,
            packetSize = monitor.config.packetSize,
        )
        val suffix = if (!outcome.usedIcmp && !Platform.supportsIcmp) " (ICMP unavailable on ${Platform.kind})" else ""
        return if (outcome.reachable) {
            CheckResult.up(outcome.detail + suffix, outcome.roundTripMs)
        } else {
            CheckResult.down(outcome.detail + suffix)
        }
    }
}

class DnsExecutor : CheckExecutor {
    override val type = MonitorType.DNS

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val records = DnsClient.query(
            server = config.dnsResolverServer,
            port = config.dnsResolverPort,
            name = config.hostname,
            recordType = config.dnsRecordType,
            timeoutMs = monitor.timeoutSeconds * 1000L,
        )
        val elapsed = nowMs() - start

        if (records.isEmpty()) {
            return CheckResult.down("No ${config.dnsRecordType} record for ${config.hostname}", elapsed)
        }

        val variables = mapOf(
            "record" to records,
            "response_time" to listOf(elapsed.toString()),
        )

        if (config.conditions.isNotEmpty() && !ConditionEvaluator.evaluate(config.conditions, variables)) {
            return CheckResult(
                MonitorStatus.DOWN,
                "Conditions not met, got ${records.joinToString()}",
                elapsed,
                variables = variables,
            )
        }

        return CheckResult(
            status = MonitorStatus.UP,
            message = records.joinToString(", "),
            pingMs = elapsed,
            variables = variables,
        )
    }
}

/**
 * Push monitors are inverted: nothing is dialled out, the scheduled beat only verifies that a
 * client reported in within the configured interval.
 */
class PushExecutor : CheckExecutor {
    override val type = MonitorType.PUSH

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val lastPush = context.lastPushProvider(monitor.id)
            ?: return CheckResult.down("No push received yet")
        val age = nowMs() - lastPush
        val limit = monitor.intervalSeconds * 1000L
        return if (age <= limit) {
            CheckResult.up("Last push ${age / 1000}s ago")
        } else {
            CheckResult.down("No push for ${age / 1000}s, expected every ${monitor.intervalSeconds}s")
        }
    }
}

/** A group aggregates its children: it is down as soon as one child is down. */
class GroupExecutor : CheckExecutor {
    override val type = MonitorType.GROUP

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val children = context.childrenProvider(monitor.id).filter { it.active }
        if (children.isEmpty()) return CheckResult(MonitorStatus.PENDING, "Group has no active members")

        val statuses = children.map { child ->
            child to (context.lastHeartbeatProvider(child.id)?.status ?: MonitorStatus.PENDING)
        }
        val down = statuses.filter { it.second == MonitorStatus.DOWN }
        val pending = statuses.filter { it.second == MonitorStatus.PENDING }

        return when {
            down.isNotEmpty() -> CheckResult.down("${down.size}/${children.size} down: " +
                down.joinToString { it.first.name })
            pending.size == statuses.size -> CheckResult(MonitorStatus.PENDING, "Waiting for first results")
            else -> CheckResult.up("All ${children.size} members up")
        }
    }
}
