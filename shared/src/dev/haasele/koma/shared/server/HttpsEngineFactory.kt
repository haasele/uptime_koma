package dev.haasele.koma.shared.server

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer as KtorEmbeddedServer

/**
 * Platform hook for TLS listeners. Desktop JVM uses Netty + a keystore/PEM;
 * other targets return null (HTTPS is a desktop CLI feature).
 */
internal expect object HttpsEngineFactory {
    fun create(
        host: String,
        port: Int,
        certificatePath: String,
        module: Application.() -> Unit,
    ): KtorEmbeddedServer<*, *>?
}
