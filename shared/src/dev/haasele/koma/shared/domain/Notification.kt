package dev.haasele.koma.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class NotificationChannel(
    val id: Long = 0,
    val name: String = "",
    val provider: String = "local",
    val config: Map<String, String> = emptyMap(),
    val active: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
)

/** Everything a provider needs to render a message, independent of the transport. */
data class NotificationEvent(
    val monitor: Monitor?,
    val heartbeat: Heartbeat?,
    val title: String,
    val message: String,
    val isTest: Boolean = false,
) {
    val statusText: String
        get() = when (heartbeat?.status) {
            MonitorStatus.UP -> "Up"
            MonitorStatus.DOWN -> "Down"
            MonitorStatus.MAINTENANCE -> "Maintenance"
            MonitorStatus.PENDING -> "Pending"
            null -> "Test"
        }
}

enum class ConfigFieldType { TEXT, PASSWORD, URL, NUMBER, BOOLEAN, MULTILINE, SELECT }

data class ConfigField(
    val key: String,
    val label: String,
    val type: ConfigFieldType = ConfigFieldType.TEXT,
    val required: Boolean = false,
    val placeholder: String = "",
    val helpText: String = "",
    val options: List<String> = emptyList(),
    val defaultValue: String = "",
)
