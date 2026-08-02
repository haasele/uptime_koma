package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.PlatformKind
import dev.haasele.koma.shared.domain.DockerConnectionType
import dev.haasele.koma.shared.domain.DockerHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DockerContainerState(
    val running: Boolean,
    val status: String,
    val health: String?,
)

/**
 * Speaks HTTP/1.1 to the Docker Engine API directly, over either a unix socket or TCP, so no
 * Docker client library is needed on any platform.
 */
object DockerClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun inspect(host: DockerHost, container: String, timeoutMs: Long): DockerContainerState {
        if (host.connectionType == DockerConnectionType.SOCKET && Platform.kind == PlatformKind.IOS) {
            throw UnsupportedOperationException("Unix sockets to a Docker daemon are not reachable from iOS")
        }

        val path = "/containers/${container.trim('/')}/json"
        val raw = when (host.connectionType) {
            DockerConnectionType.SOCKET -> {
                val connection = openUnixSocket(host.daemon, timeoutMs)
                try {
                    connection.send(httpRequest(path, "localhost"))
                    readAll(connection)
                } finally {
                    connection.close()
                }
            }
            DockerConnectionType.TCP -> {
                val target = parseTcpDaemon(host.daemon)
                useTcp(target.first, target.second, timeoutMs, target.third) { connection ->
                    connection.send(httpRequest(path, target.first))
                    readAll(connection)
                }
            }
        }

        val body = raw.substringAfter("\r\n\r\n", "")
        require(body.isNotBlank()) { "Docker daemon returned an empty body" }
        if (raw.startsWith("HTTP/1.1 404")) throw NoSuchElementException("Container \"$container\" does not exist")

        val payload = json.parseToJsonElement(body.trimJsonChunks()).jsonObject
        val state = payload["State"]?.jsonObject ?: throw IllegalStateException("Docker response has no State")
        return DockerContainerState(
            running = state["Running"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            status = state["Status"]?.jsonPrimitive?.content ?: "unknown",
            health = (state["Health"] as? JsonObject)?.get("Status")?.jsonPrimitive?.content,
        )
    }

    private fun httpRequest(path: String, host: String): ByteArray = buildString {
        append("GET ").append(path).append(" HTTP/1.1\r\n")
        append("Host: ").append(host).append("\r\n")
        append("Accept: application/json\r\n")
        append("User-Agent: koma-native\r\n")
        append("Connection: close\r\n\r\n")
    }.encodeToByteArray()

    private suspend fun readAll(connection: TcpConnection): String {
        val builder = StringBuilder()
        while (true) {
            val chunk = connection.receive(8192)
            if (chunk.isEmpty()) break
            builder.append(chunk.decodeToString())
            if (builder.length > 1_000_000) break
        }
        return builder.toString()
    }

    /** Returns host, port and whether TLS is required. */
    private fun parseTcpDaemon(daemon: String): Triple<String, Int, Boolean> {
        val useTls = daemon.startsWith("https://")
        val withoutScheme = daemon.substringAfter("://", daemon)
        val host = withoutScheme.substringBefore(':').substringBefore('/')
        val port = withoutScheme.substringAfter(':', "").substringBefore('/').toIntOrNull()
            ?: if (useTls) 2376 else 2375
        return Triple(host.ifBlank { "localhost" }, port, useTls)
    }

    /** Chunked transfer encoding wraps the JSON in size markers that must be stripped. */
    private fun String.trimJsonChunks(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else this
    }
}
