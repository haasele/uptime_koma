package dev.haasele.koma.shared.net

data class PingOutcome(
    val reachable: Boolean,
    val roundTripMs: Long?,
    val detail: String,
    /** False when the platform had to substitute a TCP probe for a real ICMP echo. */
    val usedIcmp: Boolean,
)

expect object IcmpPing {
    suspend fun ping(host: String, timeoutMs: Long, packetSize: Int): PingOutcome
}
