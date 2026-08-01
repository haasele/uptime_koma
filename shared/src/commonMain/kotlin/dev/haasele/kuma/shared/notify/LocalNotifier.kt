package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.domain.ConfigField
import dev.haasele.koma.shared.domain.NotificationEvent
import io.ktor.client.HttpClient

enum class NotificationLevel { INFO, WARNING, ERROR }

/** Bridges to the operating system notification centre; implemented per platform. */
interface LocalNotifier {
    suspend fun notify(title: String, message: String, level: NotificationLevel)
}

object NoopLocalNotifier : LocalNotifier {
    override suspend fun notify(title: String, message: String, level: NotificationLevel) = Unit
}

/**
 * Set once during start up. A holder keeps the provider registry uniform instead of giving the
 * local channel a special code path through the dispatcher.
 */
object LocalNotifierHolder {
    var current: LocalNotifier = NoopLocalNotifier
}

object LocalDeviceProvider : NotificationProvider {
    override val id = "local"
    override val displayName = "This device"
    override val fields: List<ConfigField> = emptyList()

    override suspend fun send(config: Map<String, String>, event: NotificationEvent, http: HttpClient) {
        val level = when (event.statusText) {
            "Down" -> NotificationLevel.ERROR
            "Maintenance" -> NotificationLevel.WARNING
            else -> NotificationLevel.INFO
        }
        LocalNotifierHolder.current.notify(NotificationText.title(event), NotificationText.body(event), level)
    }
}
