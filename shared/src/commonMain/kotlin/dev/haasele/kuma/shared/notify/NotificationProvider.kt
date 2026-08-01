package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.domain.ConfigField
import dev.haasele.koma.shared.domain.NotificationEvent
import io.ktor.client.HttpClient

/**
 * Providers describe their own configuration so the settings UI can render a form for any
 * transport without knowing about it. Adding a provider means adding one class.
 */
interface NotificationProvider {
    val id: String
    val displayName: String
    val fields: List<ConfigField>

    /** Throws on failure; the dispatcher turns that into a visible error. */
    suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient)

    fun validate(config: Map<String, String>): List<String> =
        fields.filter { it.required && config[it.key].isNullOrBlank() }.map { "${it.label} is required" }
}

object NotificationRegistry {

    private val providers: Map<String, NotificationProvider> = listOf(
        LocalDeviceProvider,
        WebhookProvider,
        DiscordProvider,
        SlackProvider,
        TelegramProvider,
        GotifyProvider,
        NtfyProvider,
        PushoverProvider,
        PagerDutyProvider,
        MatrixProvider,
        MattermostProvider,
        RocketChatProvider,
        HomeAssistantProvider,
        GoogleChatProvider,
        TeamsProvider,
        OpsgenieProvider,
        PushbulletProvider,
        SignalProvider,
        SmtpEmailProvider,
        BarkProvider,
        DingTalkProvider,
        FeishuProvider,
        WeComProvider,
        ZulipProvider,
        TwilioProvider,
        ServerChanProvider,
        GrafanaOnCallProvider,
    ).associateBy { it.id }

    val all: List<NotificationProvider> get() = providers.values.sortedBy { it.displayName }

    fun byId(id: String): NotificationProvider? = providers[id]
}

/** Shared message formatting so every channel produces recognisable, consistent text. */
object NotificationText {

    fun title(event: NotificationEvent): String = when {
        event.isTest -> "Uptime Koma test notification"
        event.monitor != null -> "[${event.statusText}] ${event.monitor.name}"
        else -> event.title
    }

    fun body(event: NotificationEvent): String = buildString {
        append(event.message)
        event.monitor?.let { monitor ->
            if (monitor.displayTarget.isNotBlank()) append("\n").append(monitor.displayTarget)
        }
        event.heartbeat?.let { heartbeat ->
            heartbeat.pingMs?.let { append("\nResponse time: ").append(it).append(" ms") }
        }
    }

    fun colorFor(event: NotificationEvent): Int = when (event.statusText) {
        "Up" -> 0x2ECC71
        "Down" -> 0xE74C3C
        "Maintenance" -> 0x3498DB
        else -> 0x95A5A6
    }
}
