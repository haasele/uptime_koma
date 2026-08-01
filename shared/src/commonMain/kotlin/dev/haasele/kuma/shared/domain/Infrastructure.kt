package dev.haasele.koma.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: Long = 0,
    val name: String = "",
    val color: String = "#4B5563",
)

@Serializable
data class ProxyServer(
    val id: Long = 0,
    val protocol: String = "http",
    val host: String = "",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
    val active: Boolean = true,
    val isDefault: Boolean = false,
)

@Serializable
enum class DockerConnectionType { SOCKET, TCP }

@Serializable
data class DockerHost(
    val id: Long = 0,
    val name: String = "",
    val connectionType: DockerConnectionType = DockerConnectionType.SOCKET,
    val daemon: String = "/var/run/docker.sock",
)

@Serializable
data class ApiKey(
    val id: Long = 0,
    val name: String = "",
    val prefix: String = "",
    val active: Boolean = true,
    val expiresAt: Long? = null,
    val createdAt: Long = 0,
)

@Serializable
data class AppUser(
    val id: Long = 0,
    val username: String = "",
    val twoFactorEnabled: Boolean = false,
)

@Serializable
data class AppSettings(
    val checkUpdate: Boolean = false,
    val keepHeartbeatDays: Int = 180,
    val entryPage: String = "dashboard",
    val primaryBaseUrl: String = "",
    val timezone: String = "system",
    val theme: String = "system",
    val language: String = "en",
    val embeddedServerEnabled: Boolean = true,
    val embeddedServerPort: Int = 3001,
    val remoteAccessEnabled: Boolean = false,
    val remoteAccessToken: String = "",
    val metricsEnabled: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val startEngineOnLaunch: Boolean = true,
) {
    companion object {
        const val KEY_CHECK_UPDATE = "checkUpdate"
        const val KEY_KEEP_HEARTBEAT_DAYS = "keepHeartbeatDays"
        const val KEY_ENTRY_PAGE = "entryPage"
        const val KEY_PRIMARY_BASE_URL = "primaryBaseUrl"
        const val KEY_TIMEZONE = "timezone"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_EMBEDDED_SERVER_ENABLED = "embeddedServerEnabled"
        const val KEY_EMBEDDED_SERVER_PORT = "embeddedServerPort"
        const val KEY_REMOTE_ACCESS_ENABLED = "remoteAccessEnabled"
        const val KEY_REMOTE_ACCESS_TOKEN = "remoteAccessToken"
        const val KEY_METRICS_ENABLED = "metricsEnabled"
        const val KEY_NOTIFICATION_SOUND = "notificationSoundEnabled"
        const val KEY_START_ENGINE_ON_LAUNCH = "startEngineOnLaunch"
    }
}
