package dev.haasele.koma.shared.engine.executors

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.engine.CheckContext
import dev.haasele.koma.shared.engine.CheckExecutor
import dev.haasele.koma.shared.net.HttpClientSpec
import dev.haasele.koma.shared.net.MqttProbe
import dev.haasele.koma.shared.net.SteamQuery
import dev.haasele.koma.shared.net.useTcp
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout

class WebSocketExecutor : CheckExecutor {
    override val type = MonitorType.WEBSOCKET

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val client = context.httpClients.get(
            HttpClientSpec(
                ignoreTls = monitor.config.ignoreTls,
                timeoutMs = monitor.timeoutSeconds * 1000L,
                proxy = context.proxyProvider(monitor.proxyId),
            ),
        )
        val start = nowMs()
        var handshakeMessage = "Handshake completed"
        withTimeout(monitor.timeoutSeconds * 1000L) {
            client.webSocket(monitor.config.url) {
                val frame = kotlinx.coroutines.withTimeoutOrNull(1_000L) { incoming.receiveCatching().getOrNull() }
                if (frame is Frame.Text) {
                    handshakeMessage = "Handshake completed, first frame: ${frame.readText().take(80)}"
                }
                close()
            }
        }
        return CheckResult.up(handshakeMessage, nowMs() - start)
    }
}

class MqttExecutor : CheckExecutor {
    override val type = MonitorType.MQTT

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val received = MqttProbe.connectAndRead(
            host = config.hostname,
            port = config.port,
            username = config.mqttUsername.takeIf { it.isNotBlank() },
            password = config.mqttPassword.takeIf { it.isNotBlank() },
            topic = config.mqttTopic.takeIf { it.isNotBlank() },
            useTls = config.useTls,
            timeoutMs = monitor.timeoutSeconds * 1000L,
        )
        val elapsed = nowMs() - start

        if (config.mqttTopic.isBlank()) return CheckResult.up("Broker accepted the connection", elapsed)
        val payload = received ?: return CheckResult.down("No message on ${config.mqttTopic}", elapsed)

        return when {
            config.mqttSuccessMessage.isBlank() -> CheckResult.up("Received: ${payload.take(80)}", elapsed)
            payload.contains(config.mqttSuccessMessage) -> CheckResult.up("Received expected payload", elapsed)
            else -> CheckResult.down("Expected \"${config.mqttSuccessMessage}\" but got \"${payload.take(80)}\"", elapsed)
        }
    }
}

class SteamExecutor : CheckExecutor {
    override val type = MonitorType.STEAM

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val start = nowMs()
        val info = SteamQuery.info(
            host = monitor.config.hostname,
            port = monitor.config.port,
            timeoutMs = monitor.timeoutSeconds * 1000L,
        )
        val elapsed = nowMs() - start
        return CheckResult.up("${info.name} - ${info.players}/${info.maxPlayers} players on ${info.map}", elapsed)
    }
}

class RedisExecutor : CheckExecutor {
    override val type = MonitorType.REDIS

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val reply = useTcp(config.hostname, config.port, monitor.timeoutSeconds * 1000L, config.useTls) { connection ->
            if (config.databasePassword.isNotBlank()) {
                connection.send(respCommand(listOf("AUTH", config.databasePassword)))
                val authReply = connection.readLine()
                if (authReply.startsWith("-")) return@useTcp authReply
            }
            connection.send(respCommand(listOf("PING")))
            connection.readLine()
        }
        val elapsed = nowMs() - start
        return if (reply.startsWith("+PONG")) {
            CheckResult.up("PONG", elapsed)
        } else {
            CheckResult.down("Unexpected reply: $reply", elapsed)
        }
    }

    private fun respCommand(parts: List<String>): ByteArray {
        val builder = StringBuilder("*${parts.size}\r\n")
        parts.forEach { builder.append("$${it.length}\r\n").append(it).append("\r\n") }
        return builder.toString().encodeToByteArray()
    }
}

class PostgresExecutor : CheckExecutor {
    override val type = MonitorType.POSTGRES

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val response = useTcp(config.hostname, config.port, monitor.timeoutSeconds * 1000L) { connection ->
            connection.send(startupMessage(config.databaseUser.ifBlank { "postgres" }, config.databaseName))
            connection.receive(64)
        }
        val elapsed = nowMs() - start

        if (response.isEmpty()) return CheckResult.down("Server closed the connection", elapsed)
        return when (response[0].toInt().toChar()) {
            // 'R' is an authentication request, which already proves the server is serving queries.
            'R' -> CheckResult.up("Server responded to the startup handshake", elapsed)
            'E' -> CheckResult.down("Server rejected the handshake: ${readErrorMessage(response)}", elapsed)
            else -> CheckResult.down("Unexpected response type '${response[0].toInt().toChar()}'", elapsed)
        }
    }

    private fun startupMessage(user: String, database: String): ByteArray {
        val parameters = buildString {
            append("user").append('\u0000').append(user).append('\u0000')
            if (database.isNotBlank()) append("database").append('\u0000').append(database).append('\u0000')
        }.encodeToByteArray() + byteArrayOf(0)

        val length = 4 + 4 + parameters.size
        val out = ByteArray(length)
        writeInt(out, 0, length)
        writeInt(out, 4, 196_608) // protocol version 3.0
        parameters.copyInto(out, 8)
        return out
    }

    private fun readErrorMessage(response: ByteArray): String =
        response.drop(5).map { it.toInt().toChar() }.joinToString("").filter { it.isLetterOrDigit() || it == ' ' }.trim()
}

class MySqlExecutor : CheckExecutor {
    override val type = MonitorType.MYSQL

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val greeting = useTcp(config.hostname, config.port, monitor.timeoutSeconds * 1000L) { connection ->
            connection.receive(256)
        }
        val elapsed = nowMs() - start

        if (greeting.size < 5) return CheckResult.down("No handshake packet received", elapsed)
        val protocolVersion = greeting[4].toInt() and 0xff
        if (protocolVersion == 0xff) {
            val message = greeting.drop(7).map { it.toInt().toChar() }.joinToString("").trim()
            return CheckResult.down("Server refused the connection: $message", elapsed)
        }
        val serverVersion = greeting.drop(5).takeWhile { it.toInt() != 0 }.map { it.toInt().toChar() }.joinToString("")
        return CheckResult.up("Handshake from MySQL $serverVersion", elapsed)
    }
}

class MongoExecutor : CheckExecutor {
    override val type = MonitorType.MONGODB

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val start = nowMs()
        val response = useTcp(config.hostname, config.port, monitor.timeoutSeconds * 1000L) { connection ->
            connection.send(isMasterQuery())
            connection.receive(512)
        }
        val elapsed = nowMs() - start

        if (response.size < 16) return CheckResult.down("No reply to the hello command", elapsed)
        val text = response.map { byte ->
            val code = byte.toInt() and 0xff
            if (code in 32..126) code.toChar() else ' '
        }.joinToString("")
        return if (text.contains("ismaster") || text.contains("maxBsonObjectSize") || text.contains("ok")) {
            CheckResult.up("Server answered the hello command", elapsed)
        } else {
            CheckResult.down("Unexpected reply to the hello command", elapsed)
        }
    }

    /** Legacy OP_QUERY `{ismaster: 1}` against `admin.$cmd`, understood by every server version. */
    private fun isMasterQuery(): ByteArray {
        val document = bsonIsMaster()
        val collection = "admin.\$cmd".encodeToByteArray() + byteArrayOf(0)
        val bodyLength = 4 + collection.size + 4 + 4 + document.size
        val totalLength = 16 + bodyLength
        val out = ByteArray(totalLength)

        writeIntLe(out, 0, totalLength)
        writeIntLe(out, 4, 1) // requestId
        writeIntLe(out, 8, 0) // responseTo
        writeIntLe(out, 12, 2004) // OP_QUERY
        writeIntLe(out, 16, 0) // flags
        collection.copyInto(out, 20)
        writeIntLe(out, 20 + collection.size, 0) // numberToSkip
        writeIntLe(out, 24 + collection.size, 1) // numberToReturn
        document.copyInto(out, 28 + collection.size)
        return out
    }

    private fun bsonIsMaster(): ByteArray {
        val key = "ismaster".encodeToByteArray()
        val size = 4 + 1 + key.size + 1 + 4 + 1
        val out = ByteArray(size)
        writeIntLe(out, 0, size)
        out[4] = 0x10 // int32
        key.copyInto(out, 5)
        out[5 + key.size] = 0
        writeIntLe(out, 6 + key.size, 1)
        out[size - 1] = 0
        return out
    }
}

internal fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}

internal fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
    target[offset] = value.toByte()
    target[offset + 1] = (value ushr 8).toByte()
    target[offset + 2] = (value ushr 16).toByte()
    target[offset + 3] = (value ushr 24).toByte()
}

/** Placed here so unsupported platforms produce a clear message instead of a socket error. */
internal fun unsupported(type: MonitorType, reason: String): CheckResult =
    CheckResult(MonitorStatus.DOWN, "${type.label} is not available on this platform: $reason")
