package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.crypto.Sha256
import dev.haasele.koma.shared.crypto.base64
import dev.haasele.koma.shared.crypto.hmac
import dev.haasele.koma.shared.core.nowMs
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
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object BarkProvider : NotificationProvider {
    override val id = "bark"
    override val displayName = "Bark"
    override val fields = listOf(
        ConfigField("serverUrl", "Server URL", ConfigFieldType.URL, defaultValue = "https://api.day.app"),
        ConfigField("deviceKey", "Device key", ConfigFieldType.PASSWORD, required = true),
        ConfigField("sound", "Sound", defaultValue = "telegraph"),
        ConfigField("group", "Group", defaultValue = "KomaNative"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val server = config["serverUrl"]?.takeIf { it.isNotBlank() } ?: "https://api.day.app"
        val title = NotificationText.title(event).encodeURLParameter()
        val body = NotificationText.body(event).encodeURLParameter()
        val endpoint = buildString {
            append(server.trimEnd('/')).append('/').append(config.getValue("deviceKey"))
            append('/').append(title).append('/').append(body)
            append("?sound=").append(config["sound"].orEmpty())
            append("&group=").append(config["group"].orEmpty())
        }
        http.post(endpoint).requireSuccess(displayName)
    }
}

/** DingTalk signs each request with the shared secret, which is why it needs its own provider. */
object DingTalkProvider : NotificationProvider {
    override val id = "dingtalk"
    override val displayName = "DingTalk"
    override val fields = listOf(
        ConfigField("accessToken", "Access token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("secret", "Signing secret", ConfigFieldType.PASSWORD),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val endpoint = buildString {
            append("https://oapi.dingtalk.com/robot/send?access_token=")
            append(config.getValue("accessToken"))
            config["secret"]?.takeIf { it.isNotBlank() }?.let { secret ->
                val timestamp = nowMs()
                val signature = hmac(
                    digest = Sha256,
                    key = secret.encodeToByteArray(),
                    message = "$timestamp\n$secret".encodeToByteArray(),
                ).base64().encodeURLParameter()
                append("&timestamp=").append(timestamp).append("&sign=").append(signature)
            }
        }

        val payload = buildJsonObject {
            put("msgtype", "text")
            putJsonObject("text") {
                put("content", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
            }
        }
        http.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object FeishuProvider : NotificationProvider {
    override val id = "feishu"
    override val displayName = "Feishu / Lark"
    override val fields = listOf(
        ConfigField("webhookUrl", "Webhook URL", ConfigFieldType.URL, required = true),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("msg_type", "text")
            putJsonObject("content") {
                put("text", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
            }
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object WeComProvider : NotificationProvider {
    override val id = "wecom"
    override val displayName = "WeCom"
    override val fields = listOf(
        ConfigField("botKey", "Bot key", ConfigFieldType.PASSWORD, required = true),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("msgtype", "text")
            putJsonObject("text") {
                put("content", "${NotificationText.title(event)}\n${NotificationText.body(event)}")
            }
        }
        http.post("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=${config.getValue("botKey")}") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}

object ZulipProvider : NotificationProvider {
    override val id = "zulip"
    override val displayName = "Zulip"
    override val fields = listOf(
        ConfigField("serverUrl", "Server URL", ConfigFieldType.URL, required = true),
        ConfigField("botEmail", "Bot email", required = true),
        ConfigField("botKey", "Bot API key", ConfigFieldType.PASSWORD, required = true),
        ConfigField("stream", "Stream", required = true),
        ConfigField("topic", "Topic", defaultValue = "Monitoring"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val server = config.getValue("serverUrl").trimEnd('/')
        val credentials = "${config.getValue("botEmail")}:${config.getValue("botKey")}".base64()
        http.submitForm(
            url = "$server/api/v1/messages",
            formParameters = Parameters.build {
                append("type", "stream")
                append("to", config.getValue("stream"))
                append("topic", config["topic"]?.takeIf { it.isNotBlank() } ?: "Monitoring")
                append("content", "**${NotificationText.title(event)}**\n${NotificationText.body(event)}")
            },
        ) {
            header("Authorization", "Basic $credentials")
        }.requireSuccess(displayName)
    }
}

object TwilioProvider : NotificationProvider {
    override val id = "twilio"
    override val displayName = "Twilio SMS"
    override val fields = listOf(
        ConfigField("accountSid", "Account SID", required = true),
        ConfigField("authToken", "Auth token", ConfigFieldType.PASSWORD, required = true),
        ConfigField("fromNumber", "From number", required = true, placeholder = "+15550000000"),
        ConfigField("toNumber", "To number", required = true, placeholder = "+15551111111"),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val sid = config.getValue("accountSid")
        val credentials = "$sid:${config.getValue("authToken")}".base64()
        http.submitForm(
            url = "https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json",
            formParameters = Parameters.build {
                append("From", config.getValue("fromNumber"))
                append("To", config.getValue("toNumber"))
                append("Body", "${NotificationText.title(event)} — ${NotificationText.body(event).lineSequence().first()}")
            },
        ) {
            header("Authorization", "Basic $credentials")
        }.requireSuccess(displayName)
    }
}

object ServerChanProvider : NotificationProvider {
    override val id = "serverchan"
    override val displayName = "ServerChan"
    override val fields = listOf(
        ConfigField("sendKey", "Send key", ConfigFieldType.PASSWORD, required = true),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        http.submitForm(
            url = "https://sctapi.ftqq.com/${config.getValue("sendKey")}.send",
            formParameters = Parameters.build {
                append("title", NotificationText.title(event))
                append("desp", NotificationText.body(event))
            },
        ).requireSuccess(displayName)
    }
}

object GrafanaOnCallProvider : NotificationProvider {
    override val id = "grafana-oncall"
    override val displayName = "Grafana OnCall"
    override val fields = listOf(
        ConfigField("webhookUrl", "Integration URL", ConfigFieldType.URL, required = true),
    )

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val payload = buildJsonObject {
            put("title", NotificationText.title(event))
            put("message", NotificationText.body(event))
            put("state", if (event.statusText == "Down") "alerting" else "ok")
            event.monitor?.let { put("alert_uid", "koma-native-${it.id}") }
        }
        http.post(config.getValue("webhookUrl")) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }.requireSuccess(displayName)
    }
}
