package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.domain.ConfigField
import dev.haasele.koma.shared.domain.ConfigFieldType
import dev.haasele.koma.shared.domain.NotificationEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun HttpResponse.requireSuccess(provider: String) {
    if (!status.isSuccess()) {
        val body = runCatching { bodyAsText() }.getOrDefault("")
        throw NotificationException("$provider responded ${status.value}: ${body.take(200)}")
    }
}

class NotificationException(message: String) : Exception(message)

object WebhookProvider : NotificationProvider {
    override val id = "webhook"
    override val displayName = "Webhook"
    override val fields = listOf(
        ConfigField("url", "Post URL", ConfigFieldType.URL, required = true, placeholder = "https://example.com/hook"),
        ConfigField("additionalHeaders", "Additional headers", ConfigFieldType.MULTILINE,
            helpText = "One per line as Name: Value"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("heartbeat", buildJsonObject {
                put("status", event.statusText)
                put("msg", event.message)
                put("time", event.heartbeat?.timeMs ?: 0L)
                put("ping", JsonPrimitive(event.heartbeat?.pingMs))
            })
            put("monitor", buildJsonObject {
                put("id", event.monitor?.id ?: 0L)
                put("name", event.monitor?.name ?: "")
                put("type", event.monitor?.type?.id ?: "")
                put("target", event.monitor?.displayTarget ?: "")
            })
            put("msg", NotificationText.body(event))
        }

        http.post(config.getValue("url")) {
            contentType(ContentType.Application.Json)
            config["additionalHeaders"]?.lines()?.forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) header(line.take(separator).trim(), line.drop(separator + 1).trim())
            }
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object DiscordProvider : NotificationProvider {
    override val id = "discord"
    override val displayName = "Discord"
    override val fields = listOf(
        ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true),
        ConfigField("username", "Bot display name", placeholder = "Uptime Koma"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("username", config["username"]?.takeIf { it.isNotBlank() } ?: "Uptime Koma")
            put("embeds", buildJsonArray {
                add(
                    buildJsonObject {
                        put("title", NotificationText.title(event))
                        put("description", NotificationText.body(event))
                        put("color", NotificationText.colorFor(event))
                    },
                )
            })
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object SlackProvider : NotificationProvider {
    override val id = "slack"
    override val displayName = "Slack"
    override val fields = listOf(
        ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true),
        ConfigField("channel", "Channel", placeholder = "#alerts"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("text", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
            config["channel"]?.takeIf { it.isNotBlank() }?.let { put("channel", it) }
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object MattermostProvider : NotificationProvider {
    override val id = "mattermost"
    override val displayName = "Mattermost"
    override val fields = listOf(
        ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true),
        ConfigField("channel", "Channel"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("text", "**${NotificationText.title(event)}**\n${NotificationText.body(event)}")
            config["channel"]?.takeIf { it.isNotBlank() }?.let { put("channel", it) }
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object RocketChatProvider : NotificationProvider {
    override val id = "rocketchat"
    override val displayName = "Rocket.Chat"
    override val fields = listOf(
        ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true),
        ConfigField("channel", "Channel"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("text", NotificationText.title(event))
            config["channel"]?.takeIf { it.isNotBlank() }?.let { put("channel", it) }
            put("attachments", buildJsonArray {
                add(buildJsonObject { put("text", NotificationText.body(event)) })
            })
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object GoogleChatProvider : NotificationProvider {
    override val id = "googlechat"
    override val displayName = "Google Chat"
    override val fields = listOf(ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true))

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("text", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object TeamsProvider : NotificationProvider {
    override val id = "teams"
    override val displayName = "Microsoft Teams"
    override val fields = listOf(ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true))

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("@type", "MessageCard")
            put("@context", "https://schema.org/extensions")
            put("themeColor", NotificationText.colorFor(event).toString(16).padStart(6, '0'))
            put("summary", NotificationText.title(event))
            put("title", NotificationText.title(event))
            put("text", NotificationText.body(event))
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object HomeAssistantProvider : NotificationProvider {
    override val id = "homeassistant"
    override val displayName = "Home Assistant"
    override val fields = listOf(
        ConfigField("baseUrl", "Home Assistant URL", ConfigFieldType.URL, required = true),
        ConfigField("token", "Long lived access token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("service", "Notification service", defaultValue = "notify.persistent_notification"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val service = config["service"]?.takeIf { it.isNotBlank() } ?: "notify.persistent_notification"
        val endpoint = "${config.getValue("baseUrl").trimEnd('/')}/api/services/${service.replace('.', '/')}"
        val payload = buildJsonObject {
            put("title", NotificationText.title(event))
            put("message", NotificationText.body(event))
        }
        http.post(endpoint) {
            header("Authorization", "Bearer ${config.getValue("token")}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}