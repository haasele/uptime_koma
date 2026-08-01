package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CertificateInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

internal actual fun buildHttpClient(spec: HttpClientSpec): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    followRedirects = spec.followRedirects

    engine {
        configureRequest {
            setTimeoutInterval(spec.timeoutMs / 1000.0)
        }
        if (spec.ignoreTls) {
            handleChallenge { _, _, challenge, completionHandler ->
                val trust = challenge.protectionSpace.serverTrust
                completionHandler(
                    platform.Foundation.NSURLSessionAuthChallengeDisposition.NSURLSessionAuthChallengeUseCredential,
                    trust?.let { platform.Foundation.NSURLCredential.credentialForTrust(it) },
                )
            }
        }
    }

    install(HttpTimeout) {
        requestTimeoutMillis = spec.timeoutMs
        connectTimeoutMillis = spec.timeoutMs
        socketTimeoutMillis = spec.timeoutMs
    }

    install(io.ktor.client.plugins.websocket.WebSockets)

    if (spec.followRedirects) {
        install(HttpRedirect) { checkHttpMethod = false }
    }
}

actual object TlsInspector {
    /** Apple's Security framework does not expose the peer chain through a Kotlin friendly API. */
    actual suspend fun inspect(host: String, port: Int, timeoutMs: Long): CertificateInfo? = null
}

actual object IcmpPing {
    /** iOS forbids raw sockets for App Store builds, so latency is measured with a TCP connect. */
    actual suspend fun ping(host: String, timeoutMs: Long, packetSize: Int): PingOutcome {
        val selector = SelectorManager(Dispatchers.Default)
        for (port in intArrayOf(443, 80)) {
            val start = nowMs()
            val socket = withTimeoutOrNull(timeoutMs) {
                runCatching { aSocket(selector).tcp().connect(InetSocketAddress(host, port)) }.getOrNull()
            }
            if (socket != null) {
                val elapsed = nowMs() - start
                socket.close()
                selector.close()
                return PingOutcome(true, elapsed, "TCP probe on port $port answered in ${elapsed}ms", usedIcmp = false)
            }
        }
        selector.close()
        return PingOutcome(false, null, "Host did not answer a TCP probe", usedIcmp = false)
    }
}
