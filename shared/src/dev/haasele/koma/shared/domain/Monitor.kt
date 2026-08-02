package dev.haasele.koma.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MonitorStatus(val code: Int) {
    @SerialName("down") DOWN(0),
    @SerialName("up") UP(1),
    @SerialName("pending") PENDING(2),
    @SerialName("maintenance") MAINTENANCE(3);

    companion object {
        fun fromCode(code: Int): MonitorStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

@Serializable
enum class MonitorType(val id: String, val label: String) {
    @SerialName("http") HTTP("http", "HTTP(s)"),
    @SerialName("keyword") KEYWORD("keyword", "HTTP(s) Keyword"),
    @SerialName("json-query") JSON_QUERY("json-query", "HTTP(s) Json Query"),
    @SerialName("port") PORT("port", "TCP Port"),
    @SerialName("ping") PING("ping", "Ping"),
    @SerialName("dns") DNS("dns", "DNS Record"),
    @SerialName("push") PUSH("push", "Push"),
    @SerialName("group") GROUP("group", "Group"),
    @SerialName("websocket") WEBSOCKET("websocket", "WebSocket"),
    @SerialName("mqtt") MQTT("mqtt", "MQTT"),
    @SerialName("docker") DOCKER("docker", "Docker Container"),
    @SerialName("steam") STEAM("steam", "Steam Game Server"),
    @SerialName("redis") REDIS("redis", "Redis"),
    @SerialName("postgres") POSTGRES("postgres", "PostgreSQL"),
    @SerialName("mysql") MYSQL("mysql", "MySQL/MariaDB"),
    @SerialName("mongodb") MONGODB("mongodb", "MongoDB"),
    @SerialName("radius") RADIUS("radius", "Radius"),
    @SerialName("snmp") SNMP("snmp", "SNMP"),
    @SerialName("rabbitmq") RABBITMQ("rabbitmq", "RabbitMQ"),
    @SerialName("manual") MANUAL("manual", "Manual");

    val usesHttp: Boolean get() = this == HTTP || this == KEYWORD || this == JSON_QUERY
    val usesHostPort: Boolean
        get() = this in setOf(PORT, STEAM, REDIS, POSTGRES, MYSQL, MONGODB, MQTT, RADIUS, SNMP)

    companion object {
        fun fromId(id: String): MonitorType = entries.firstOrNull { it.id == id } ?: HTTP
    }
}

@Serializable
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }

@Serializable
enum class HttpAuthMethod { NONE, BASIC, BEARER, NTLM }

@Serializable
enum class DnsRecordType { A, AAAA, CNAME, MX, NS, PTR, SOA, SRV, TXT, CAA }

@Serializable
enum class BodyEncoding { JSON, FORM, XML, TEXT }

/**
 * Type specific settings live in a single serialized payload. Uptime Koma spreads these over
 * dozens of nullable columns; keeping them in one structure means adding a monitor type does
 * not require a schema migration.
 */
@Serializable
data class MonitorConfig(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val bodyEncoding: BodyEncoding = BodyEncoding.JSON,
    val acceptedStatusCodes: List<String> = listOf("200-299"),
    val maxRedirects: Int = 10,
    val ignoreTls: Boolean = false,
    val authMethod: HttpAuthMethod = HttpAuthMethod.NONE,
    val basicAuthUser: String = "",
    val basicAuthPassword: String = "",
    val bearerToken: String = "",
    val certificateExpiryNotification: Boolean = true,
    val certificateExpiryDays: List<Int> = listOf(7, 14, 21),

    val keyword: String = "",
    val invertKeyword: Boolean = false,
    val jsonPath: String = "$",
    val expectedValue: String = "",

    val hostname: String = "",
    val port: Int = 0,
    val packetSize: Int = 56,

    val dnsResolverServer: String = "1.1.1.1",
    val dnsResolverPort: Int = 53,
    val dnsRecordType: DnsRecordType = DnsRecordType.A,

    val mqttTopic: String = "",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val mqttSuccessMessage: String = "",

    val dockerHostId: Long? = null,
    val dockerContainer: String = "",

    val databaseUser: String = "",
    val databasePassword: String = "",
    val databaseName: String = "",

    val radiusSecret: String = "",
    val radiusCalledStationId: String = "",
    val radiusCallingStationId: String = "",

    val snmpCommunity: String = "public",
    val snmpOid: String = "",
    val snmpVersion2c: Boolean = true,
    val snmpExpectedValue: String = "",

    val useTls: Boolean = false,

    val conditions: List<ConditionGroup> = emptyList(),
)

@Serializable
data class Monitor(
    val id: Long = 0,
    val name: String = "",
    val type: MonitorType = MonitorType.HTTP,
    val active: Boolean = true,
    val parentId: Long? = null,
    val description: String? = null,
    val intervalSeconds: Int = 60,
    val retryIntervalSeconds: Int = 60,
    val resendIntervalBeats: Int = 0,
    val maxRetries: Int = 0,
    val timeoutSeconds: Int = 48,
    val upsideDown: Boolean = false,
    val pushToken: String? = null,
    val proxyId: Long? = null,
    val weight: Int = 2000,
    val config: MonitorConfig = MonitorConfig(),
    val tags: List<TagAssignment> = emptyList(),
    val notificationIds: List<Long> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val displayTarget: String
        get() = when {
            type.usesHttp || type == MonitorType.WEBSOCKET || type == MonitorType.RABBITMQ -> config.url
            type == MonitorType.PING -> config.hostname
            type == MonitorType.DNS -> "${config.hostname} (${config.dnsRecordType})"
            type == MonitorType.DOCKER -> config.dockerContainer
            type == MonitorType.PUSH -> "push/${pushToken.orEmpty()}"
            type == MonitorType.GROUP -> ""
            type.usesHostPort -> "${config.hostname}:${config.port}"
            else -> ""
        }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Name must not be empty"
        if (intervalSeconds < 1) errors += "Interval must be at least 1 second"
        if (retryIntervalSeconds < 1) errors += "Retry interval must be at least 1 second"
        if (maxRetries < 0) errors += "Retries must not be negative"
        when {
            type.usesHttp || type == MonitorType.WEBSOCKET || type == MonitorType.RABBITMQ ->
                if (config.url.isBlank()) errors += "URL must not be empty"
            type == MonitorType.PING || type == MonitorType.DNS ->
                if (config.hostname.isBlank()) errors += "Hostname must not be empty"
            type.usesHostPort -> {
                if (config.hostname.isBlank()) errors += "Hostname must not be empty"
                if (config.port !in 1..65535) errors += "Port must be between 1 and 65535"
            }
            type == MonitorType.DOCKER -> {
                if (config.dockerContainer.isBlank()) errors += "Container name must not be empty"
                if (config.dockerHostId == null) errors += "A Docker host must be selected"
            }
            else -> Unit
        }
        if (type == MonitorType.SNMP && config.snmpOid.isBlank()) errors += "OID must not be empty"
        if (type == MonitorType.RADIUS && config.radiusSecret.isBlank()) errors += "Shared secret must not be empty"
        if (type == MonitorType.KEYWORD && config.keyword.isBlank()) errors += "Keyword must not be empty"
        if (type == MonitorType.JSON_QUERY && config.jsonPath.isBlank()) errors += "JSON path must not be empty"
        return errors
    }

}

@Serializable
data class TagAssignment(
    val tagId: Long,
    val name: String,
    val color: String,
    val value: String? = null,
)

/** Parses Uptime Koma style status code ranges such as `200-299` or `301`. */
fun List<String>.matchesStatusCode(code: Int): Boolean = any { entry ->
    val trimmed = entry.trim()
    if (trimmed.contains('-')) {
        val bounds = trimmed.split('-', limit = 2)
        val start = bounds[0].trim().toIntOrNull()
        val end = bounds[1].trim().toIntOrNull()
        start != null && end != null && code in start..end
    } else {
        trimmed.toIntOrNull() == code
    }
}
