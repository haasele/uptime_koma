package dev.haasele.koma.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class Heartbeat(
    val id: Long = 0,
    val monitorId: Long,
    val status: MonitorStatus,
    val message: String = "",
    val pingMs: Long? = null,
    val important: Boolean = false,
    val timeMs: Long,
    val durationSeconds: Long = 0,
    val retries: Int = 0,
    val downCount: Int = 0,
)

@Serializable
data class CertificateInfo(
    val subject: String = "",
    val issuer: String = "",
    val validToMs: Long = 0,
    val daysRemaining: Int = 0,
    val valid: Boolean = false,
)

/** The outcome of a single check, before retry and maintenance rules are applied. */
data class CheckResult(
    val status: MonitorStatus,
    val message: String,
    val pingMs: Long? = null,
    val certificate: CertificateInfo? = null,
    val variables: Map<String, List<String>> = emptyMap(),
) {
    companion object {
        fun up(message: String = "OK", pingMs: Long? = null, certificate: CertificateInfo? = null) =
            CheckResult(MonitorStatus.UP, message, pingMs, certificate)

        fun down(message: String, pingMs: Long? = null) = CheckResult(MonitorStatus.DOWN, message, pingMs)
    }
}

@Serializable
data class UptimeStats(
    val monitorId: Long,
    val uptime24h: Double = 0.0,
    val uptime7d: Double = 0.0,
    val uptime30d: Double = 0.0,
    val uptime1y: Double = 0.0,
    val avgPing24h: Double? = null,
    val currentStatus: MonitorStatus = MonitorStatus.PENDING,
    val lastCheckMs: Long? = null,
    val certificate: CertificateInfo? = null,
)
