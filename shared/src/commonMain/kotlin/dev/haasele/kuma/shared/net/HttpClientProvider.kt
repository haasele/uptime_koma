package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.domain.ProxyServer
import io.ktor.client.HttpClient

data class HttpClientSpec(
    val ignoreTls: Boolean = false,
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 10,
    val timeoutMs: Long = 48_000,
    val proxy: ProxyServer? = null,
)

internal expect fun buildHttpClient(spec: HttpClientSpec): HttpClient

/**
 * Ktor clients are expensive to create, so one is kept per distinct transport configuration
 * instead of per check.
 */
class HttpClientProvider {

    private val clients = mutableMapOf<HttpClientSpec, HttpClient>()

    fun get(spec: HttpClientSpec): HttpClient = clients.getOrPut(spec) { buildHttpClient(spec) }

    fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }
}
