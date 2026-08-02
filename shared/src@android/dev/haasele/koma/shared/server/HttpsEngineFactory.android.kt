package dev.haasele.koma.shared.server

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer as KtorEmbeddedServer

internal actual object HttpsEngineFactory {
    actual fun create(
        host: String,
        port: Int,
        certificatePath: String,
        module: Application.() -> Unit,
    ): KtorEmbeddedServer<*, *>? = null
}
