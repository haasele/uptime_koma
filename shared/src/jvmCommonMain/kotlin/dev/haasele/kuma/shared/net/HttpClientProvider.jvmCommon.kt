package dev.haasele.koma.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

internal actual fun buildHttpClient(spec: HttpClientSpec): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    followRedirects = spec.followRedirects

    engine {
        requestTimeout = spec.timeoutMs
        spec.proxy?.let { proxy ->
            this.proxy = ProxyBuilder.http(Url("${proxy.protocol}://${proxy.host}:${proxy.port}"))
        }
        if (spec.ignoreTls) {
            https {
                trustManager = TrustAllManager
                random = SecureRandom()
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
        install(HttpRedirect) {
            checkHttpMethod = false
        }
    }
}

/** Only installed when a monitor explicitly opts out of certificate validation. */
private object TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun trustAllSslContext(): SSLContext = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(TrustAllManager), SecureRandom())
}
