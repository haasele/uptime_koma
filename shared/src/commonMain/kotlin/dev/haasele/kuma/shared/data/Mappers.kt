package dev.haasele.koma.shared.data

import dev.haasele.koma.shared.core.KomaJson
import dev.haasele.koma.shared.domain.ApiKey
import dev.haasele.koma.shared.domain.DockerConnectionType
import dev.haasele.koma.shared.domain.DockerHost
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Incident
import dev.haasele.koma.shared.domain.IncidentStyle
import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.MaintenanceStrategy
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorConfig
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.domain.ProxyServer
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.Tag
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import dev.haasele.koma.shared.db.Api_key as ApiKeyRow
import dev.haasele.koma.shared.db.Docker_host as DockerHostRow
import dev.haasele.koma.shared.db.Heartbeat as HeartbeatRow
import dev.haasele.koma.shared.db.Incident as IncidentRow
import dev.haasele.koma.shared.db.Maintenance as MaintenanceRow
import dev.haasele.koma.shared.db.Monitor as MonitorRow
import dev.haasele.koma.shared.db.Notification_channel as NotificationRow
import dev.haasele.koma.shared.db.Proxy as ProxyRow
import dev.haasele.koma.shared.db.Status_page as StatusPageRow
import dev.haasele.koma.shared.db.Tag as TagRow

private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val longListSerializer = kotlinx.serialization.builtins.ListSerializer(Long.serializer())
private val intListSerializer = kotlinx.serialization.builtins.ListSerializer(Int.serializer())

internal fun decodeConfig(raw: String): MonitorConfig =
    runCatching { KomaJson.decodeFromString(MonitorConfig.serializer(), raw) }.getOrElse { MonitorConfig() }

internal fun encodeConfig(config: MonitorConfig): String =
    KomaJson.encodeToString(MonitorConfig.serializer(), config)

internal fun decodeStringMap(raw: String?): Map<String, String> =
    if (raw.isNullOrBlank()) emptyMap()
    else runCatching { KomaJson.decodeFromString(stringMapSerializer, raw) }.getOrElse { emptyMap() }

internal fun encodeStringMap(map: Map<String, String>): String = KomaJson.encodeToString(stringMapSerializer, map)

internal fun decodeIntList(raw: String?): List<Int> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { KomaJson.decodeFromString(intListSerializer, raw) }.getOrElse { emptyList() }

internal fun encodeIntList(values: List<Int>): String = KomaJson.encodeToString(intListSerializer, values)

internal fun decodeLongList(raw: String?): List<Long> =
    if (raw.isNullOrBlank()) emptyList()
    else runCatching { KomaJson.decodeFromString(longListSerializer, raw) }.getOrElse { emptyList() }

internal fun MonitorRow.toDomain(): Monitor = Monitor(
    id = id,
    name = name,
    type = MonitorType.fromId(type),
    active = active,
    parentId = parent_id,
    description = description,
    intervalSeconds = interval_seconds.toInt(),
    retryIntervalSeconds = retry_interval_seconds.toInt(),
    resendIntervalBeats = resend_interval.toInt(),
    maxRetries = max_retries.toInt(),
    timeoutSeconds = timeout_seconds.toInt(),
    upsideDown = upside_down,
    pushToken = push_token,
    proxyId = proxy_id,
    weight = weight.toInt(),
    config = decodeConfig(config),
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun HeartbeatRow.toDomain(): Heartbeat = Heartbeat(
    id = id,
    monitorId = monitor_id,
    status = MonitorStatus.fromCode(status.toInt()),
    message = msg,
    pingMs = ping_ms,
    important = important,
    timeMs = time_ms,
    durationSeconds = duration_seconds,
    retries = retries.toInt(),
    downCount = down_count.toInt(),
)

internal fun NotificationRow.toDomain(): NotificationChannel = NotificationChannel(
    id = id,
    name = name,
    provider = provider,
    config = decodeStringMap(config),
    active = active,
    isDefault = is_default,
    createdAt = created_at,
)

internal fun TagRow.toDomain(): Tag = Tag(id = id, name = name, color = color)

internal fun StatusPageRow.toDomain(): StatusPage = StatusPage(
    id = id,
    slug = slug,
    title = title,
    description = description,
    icon = icon,
    theme = theme,
    published = published,
    showTags = show_tags,
    showUptimePercentage = show_uptime_percentage,
    showCertificateExpiry = show_certificate_expiry,
    footerText = footer_text,
    accentColor = accent_color,
    hasPassword = !password_hash.isNullOrBlank(),
    createdAt = created_at,
)

internal fun IncidentRow.toDomain(): Incident = Incident(
    id = id,
    statusPageId = status_page_id,
    title = title,
    content = content,
    style = runCatching { IncidentStyle.valueOf(style.uppercase()) }.getOrElse { IncidentStyle.WARNING },
    pinned = pin,
    createdAt = created_at,
    lastUpdatedAt = last_updated_at,
)

internal fun MaintenanceRow.toDomain(): Maintenance = Maintenance(
    id = id,
    title = title,
    description = description,
    strategy = MaintenanceStrategy.fromId(strategy),
    active = active,
    manualActive = manual_active,
    timezone = timezone,
    startMs = start_ms,
    endMs = end_ms,
    startTime = start_time,
    endTime = end_time,
    weekdays = decodeIntList(weekdays),
    daysOfMonth = decodeIntList(days_of_month),
    cron = cron,
    durationMinutes = duration_minutes?.toInt(),
    intervalDay = interval_day?.toInt(),
    createdAt = created_at,
)

internal fun ProxyRow.toDomain(): ProxyServer = ProxyServer(
    id = id,
    protocol = protocol,
    host = host,
    port = port.toInt(),
    username = username,
    password = password,
    active = active,
    isDefault = is_default,
)

internal fun DockerHostRow.toDomain(): DockerHost = DockerHost(
    id = id,
    name = name,
    connectionType = runCatching { DockerConnectionType.valueOf(connection_type.uppercase()) }
        .getOrElse { DockerConnectionType.SOCKET },
    daemon = daemon,
)

internal fun ApiKeyRow.toDomain(): ApiKey = ApiKey(
    id = id,
    name = name,
    prefix = prefix,
    active = active,
    expiresAt = expires_at,
    createdAt = created_at,
)
