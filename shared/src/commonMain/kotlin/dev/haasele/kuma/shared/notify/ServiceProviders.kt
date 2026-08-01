package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.domain.ConfigField
import dev.haasele.koma.shared.domain.ConfigFieldType
import dev.haasele.koma.shared.domain.NotificationEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import dev.haasele.koma.shared.crypto.base64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object TelegramProvider : NotificationProvider {
    override val id = "telegram"
    override val displayName = "Telegram"
    override val fields = listOf(
        ConfigField("botToken", "Bot token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("chatId", "Chat ID", required = true),
        ConfigField("serverUrl", "API server", ConfigFieldType.URL, defaultValue = "https://api.telegram.org"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val server = config["serverUrl"]?.takeIf { it.isNotBlank() } ?: "https://api.telegram.org"
        http.submitForm(
            url = "${server.trimEnd('/')}/bot${config.getValue("botToken")}/sendMessage",
            formParameters = Parameters.build {
                append("chat_id", config.getValue("chatId"))
                append("text", "${NotificationText.title(event)}\n\n${NotificationText.body(event)}")
            },
        ).requireSuccess(displayName)
    }
}

object GotifyProvider : NotificationProvider {
    override val id = "gotify"
    override val displayName = "Gotify"
    override val fields = listOf(
        ConfigField("serverUrl", "Server URL", ConfigFieldType.URL, required = true),
        ConfigField("applicationToken", "Application token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("priority", "Priority", ConfigFieldType.NUMBER, defaultValue = "8"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val endpoint = "${config.getValue("serverUrl").trimEnd('/')}/message" +
            "?token=${config.getValue("applicationToken")}"
        val payload = buildJsonObject {
            put("title", NotificationText.title(event))
            put("message", NotificationText.body(event))
            put("priority", config["priority"]?.toIntOrNull() ?: 8)
        }
        http.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object NtfyProvider : NotificationProvider {
    override val id = "ntfy"
    override val displayName = "ntfy"
    override val fields = listOf(
        ConfigField("serverUrl", "Server URL", ConfigFieldType.URL, defaultValue = "https://ntfy.sh"),
        ConfigField("topic", "Topic", required = true),
        ConfigField("priority", "Priority", ConfigFieldType.NUMBER, defaultValue = "4"),
        ConfigField("token", "Access token", ConfigFieldType.PASSWORD),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val server = config["serverUrl"]?.takeIf { it.isNotBlank() } ?: "https://ntfy.sh"
        val payload = buildJsonObject {
            put("topic", config.getValue("topic"))
            put("title", NotificationText.title(event))
            put("message", NotificationText.body(event))
            put("priority", config["priority"]?.toIntOrNull() ?: 4)
        }
        http.post(server.trimEnd('/')) {
            config["token"]?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object PushoverProvider : NotificationProvider {
    override val id = "pushover"
    override val displayName = "Pushover"
    override val fields = listOf(
        ConfigField("userKey", "User key", required = true),
        ConfigField("appToken", "Application token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("priority", "Priority", ConfigFieldType.NUMBER, defaultValue = "0"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        http.submitForm(
            url = "https://api.pushover.net/1/messages.json",
            formParameters = Parameters.build {
                append("token", config.getValue("appToken"))
                append("user", config.getValue("userKey"))
                append("title", NotificationText.title(event))
                append("message", NotificationText.body(event))
                append("priority", config["priority"] ?: "0")
            },
        ).requireSuccess(displayName)
    }
}

object PushbulletProvider : NotificationProvider {
    override val id = "pushbullet"
    override val displayName = "Pushbullet"
    override val fields = listOf(ConfigField("accessToken", "Access token", ConfigFieldType.PASSWORD, required = true))

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("type", "note")
            put("title", NotificationText.title(event))
            put("body", NotificationText.body(event))
        }
        http.post("https://api.pushbullet.com/v2/pushes") {
            header("Access-Token", config.getValue("accessToken"))
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object PagerDutyProvider : NotificationProvider {
    override val id = "pagerduty"
    override val displayName = "PagerDuty"
    override val fields = listOf(
        ConfigField("integrationKey", "Integration key", ConfigFieldType.PASSWORD, required = true),
        ConfigField("severity", "Severity", ConfigFieldType.SELECT,
            options = listOf("critical", "error", "warning", "info"), defaultValue = "error"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val resolved = event.statusText == "Up"
        val payload = buildJsonObject {
            put("routing_key", config.getValue("integrationKey"))
            put("event_action", if (resolved) "resolve" else "trigger")
            put("dedup_key", "koma-native-${event.monitor?.id ?: 0}")
            put("payload", buildJsonObject {
                put("summary", "${NotificationText.title(event)} - ${event.message}")
                put("severity", config["severity"] ?: "error")
                put("source", event.monitor?.displayTarget?.ifBlank { "koma-native" } ?: "koma-native")
            })
        }
        http.post("https://events.pagerduty.com/v2/enqueue") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object OpsgenieProvider : NotificationProvider {
    override val id = "opsgenie"
    override val displayName = "Opsgenie"
    override val fields = listOf(
        ConfigField("apiKey", "API key", ConfigFieldType.PASSWORD, required = true),
        ConfigField("region", "Region", ConfigFieldType.SELECT, options = listOf("us", "eu"), defaultValue = "us"),
        ConfigField("priority", "Priority", ConfigFieldType.SELECT,
            options = listOf("P1", "P2", "P3", "P4", "P5"), defaultValue = "P3"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val host = if (config["region"] == "eu") "api.eu.opsgenie.com" else "api.opsgenie.com"
        val resolved = event.statusText == "Up"
        val alias = "koma-native-${event.monitor?.id ?: 0}"

        if (resolved) {
            http.post("https://$host/v2/alerts/$alias/close?identifierType=alias") {
                header("Authorization", "GenieKey ${config.getValue("apiKey")}")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("note", NotificationText.body(event)) }.toString())
            }.requireSuccess(displayName)
            return
        }

        val payload = buildJsonObject {
            put("message", NotificationText.title(event))
            put("alias", alias)
            put("description", NotificationText.body(event))
            put("priority", config["priority"] ?: "P3")
        }
        http.post("https://$host/v2/alerts") {
            header("Authorization", "GenieKey ${config.getValue("apiKey")}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object MatrixProvider : NotificationProvider {
    override val id = "matrix"
    override val displayName = "Matrix"
    override val fields = listOf(
        ConfigField("homeserverUrl", "Homeserver URL", ConfigFieldType.URL, required = true),
        ConfigField("accessToken", "Access token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("roomId", "Internal room ID", required = true, placeholder = "!room:example.org"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val room = config.getValue("roomId")
        val transactionId = "koma${event.heartbeat?.timeMs ?: 0}"
        val endpoint = "${config.getValue("homeserverUrl").trimEnd('/')}" +
            "/_matrix/client/v3/rooms/$room/send/m.room.message/$transactionId"
        val payload = buildJsonObject {
            put("msgtype", "m.text")
            put("body", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
        }
        http.post(endpoint) {
            header("Authorization", "Bearer ${config.getValue("accessToken")}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object SignalProvider : NotificationProvider {
    override val id = "signal"
    override val displayName = "Signal (REST API)"
    override val fields = listOf(
        ConfigField("apiUrl", "signal-cli REST URL", ConfigFieldType.URL, required = true),
        ConfigField("number", "Sender number", required = true),
        ConfigField("recipients", "Recipients", required = true, helpText = "Comma separated"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("message", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
            put("number", config.getValue("number"))
            put("recipients", kotlinx.serialization.json.buildJsonArray {
                config.getValue("recipients").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }
        http.post("${config.getValue("apiUrl").trimEnd('/')}/v2/send") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object SmtpEmailProvider : NotificationProvider {
    override val id = "smtp"
    override val displayName = "Email (SMTP)"
    override val fields = listOf(
        ConfigField("host", "SMTP host", required = true),
        ConfigField("port", "Port", ConfigFieldType.NUMBER, required = true, defaultValue = "587"),
        ConfigField("security", "Security", ConfigFieldType.SELECT,
            options = listOf("starttls", "tls", "none"), defaultValue = "starttls"),
        ConfigField("username", "Username"),
        ConfigField("password", "Password", ConfigFieldType.PASSWORD),
        ConfigField("from", "From address", required = true),
        ConfigField("to", "To addresses", required = true, helpText = "Comma separated"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        SmtpClient.send(
            host = config.getValue("host"),
            port = config["port"]?.toIntOrNull() ?: 587,
            security = SmtpSecurity.fromId(config["security"]),
            username = config["username"]?.takeIf { it.isNotBlank() },
            password = config["password"]?.takeIf { it.isNotBlank() },
            from = config.getValue("from"),
            recipients = config.getValue("to").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            subject = NotificationText.title(event),
            body = NotificationText.body(event),
        )
    }
}

/** Kept next to the providers because only SMTP needs base64 encoded credentials. */
internal fun basicCredentials(username: String, password: String): String =
    "$username:$password".base64()
