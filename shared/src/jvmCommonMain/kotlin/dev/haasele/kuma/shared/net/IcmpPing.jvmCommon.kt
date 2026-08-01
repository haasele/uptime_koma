package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.core.ioDispatcher
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

actual object IcmpPing {

    /**
     * `InetAddress.isReachable` sends a real ICMP echo when the process is privileged and
     * silently degrades to a TCP probe on port 7 otherwise, which is why the outcome reports
     * which mechanism answered.
     */
    actual suspend fun ping(host: String, timeoutMs: Long, packetSize: Int): PingOutcome =
        withContext(ioDispatcher) {
            runCatching {
                val address = InetAddress.getByName(host)
                val start = System.nanoTime()
                val reachable = address.isReachable(timeoutMs.toInt())
                val elapsed = (System.nanoTime() - start) / 1_000_000

                if (reachable) {
                    PingOutcome(true, elapsed, "${address.hostAddress}: reply in ${elapsed}ms", usedIcmp = true)
                } else {
                    tcpFallback(host, timeoutMs)
                }
            }.getOrElse { error ->
                PingOutcome(false, null, error.message ?: "Host unreachable", usedIcmp = false)
            }
        }

    /** Used when ICMP is filtered or unavailable so the monitor still reports useful latency. */
    private fun tcpFallback(host: String, timeoutMs: Long): PingOutcome {
        for (port in intArrayOf(443, 80)) {
            val start = System.nanoTime()
            val connected = runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs.toInt()) }
                true
            }.getOrDefault(false)
            if (connected) {
                val elapsed = (System.nanoTime() - start) / 1_000_000
                return PingOutcome(true, elapsed, "TCP probe on port $port answered in ${elapsed}ms", usedIcmp = false)
            }
        }
        return PingOutcome(false, null, "No ICMP reply and no TCP fallback answer", usedIcmp = false)
    }
}
