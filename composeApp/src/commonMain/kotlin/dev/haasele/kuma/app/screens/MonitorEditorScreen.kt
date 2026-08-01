package dev.haasele.koma.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.ui.CheckRow
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaDropdown
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.NumberField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.SwitchRow
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.crypto.randomToken
import dev.haasele.koma.shared.domain.BodyEncoding
import dev.haasele.koma.shared.domain.DnsRecordType
import dev.haasele.koma.shared.domain.DockerHost
import dev.haasele.koma.shared.domain.HttpAuthMethod
import dev.haasele.koma.shared.domain.HttpMethod
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.ProxyServer
import dev.haasele.koma.shared.domain.TagAssignment
import kotlinx.coroutines.launch

@Composable
fun MonitorEditorScreen(
    core: KomaCore,
    monitorId: Long?,
    parentId: Long?,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val channels by core.notifications.observeAll().collectAsState(emptyList())
    val allTags by core.tags.observeAll().collectAsState(emptyList())
    val groups by core.monitors.observeAll().collectAsState(emptyList())

    var draft by remember { mutableStateOf<Monitor?>(null) }
    var headerText by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var proxies by remember { mutableStateOf<List<ProxyServer>>(emptyList()) }
    var dockerHosts by remember { mutableStateOf<List<DockerHost>>(emptyList()) }

    LaunchedEffect(monitorId) {
        proxies = core.infrastructure.getProxies()
        dockerHosts = core.infrastructure.getDockerHosts()
        val loaded = monitorId?.let { core.monitors.getById(it) }
            ?: Monitor(parentId = parentId, notificationIds = core.notifications.getDefaults().map { it.id })
        headerText = loaded.config.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        draft = loaded
    }

    val monitor = draft ?: return
    fun update(block: Monitor.() -> Monitor) {
        draft = monitor.block()
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                if (monitorId == null) "New monitor" else "Edit monitor",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
        }

        item {
            Column {
                KomaDropdown(
                    label = "Monitor type",
                    options = MonitorType.entries,
                    selected = monitor.type,
                    onSelect = { type ->
                        update {
                            copy(
                                type = type,
                                pushToken = if (type == MonitorType.PUSH && pushToken == null) randomToken(24) else pushToken,
                            )
                        }
                    },
                    optionLabel = { it.label },
                )
                KomaField(monitor.name, { value -> update { copy(name = value) } }, "Friendly name")
            }
        }

        item { TypeSpecificFields(monitor, dockerHosts, ::update) }

        if (monitor.type.usesHttp) {
            item {
                Column {
                    SectionTitle("HTTP options")
                    KomaDropdown(
                        label = "Method",
                        options = HttpMethod.entries,
                        selected = monitor.config.method,
                        onSelect = { value -> update { copy(config = config.copy(method = value)) } },
                        optionLabel = { it.name },
                    )
                    KomaField(
                        value = monitor.config.acceptedStatusCodes.joinToString(", "),
                        onValueChange = { value ->
                            update {
                                copy(config = config.copy(acceptedStatusCodes = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                            }
                        },
                        label = "Accepted status codes",
                        helper = "Comma separated, ranges allowed: 200-299, 301",
                    )
                    NumberField(
                        monitor.config.maxRedirects,
                        { value -> update { copy(config = config.copy(maxRedirects = value)) } },
                        "Max redirects",
                    )
                    KomaField(
                        value = headerText,
                        onValueChange = { value ->
                            headerText = value
                            update { copy(config = config.copy(headers = parseHeaders(value))) }
                        },
                        label = "Request headers",
                        helper = "One per line: Name: value",
                        singleLine = false,
                        minLines = 3,
                    )
                    KomaDropdown(
                        label = "Body encoding",
                        options = BodyEncoding.entries,
                        selected = monitor.config.bodyEncoding,
                        onSelect = { value -> update { copy(config = config.copy(bodyEncoding = value)) } },
                        optionLabel = { it.name },
                    )
                    KomaField(
                        monitor.config.body,
                        { value -> update { copy(config = config.copy(body = value)) } },
                        "Request body",
                        singleLine = false,
                        minLines = 3,
                    )
                    KomaDropdown(
                        label = "Authentication",
                        options = HttpAuthMethod.entries,
                        selected = monitor.config.authMethod,
                        onSelect = { value -> update { copy(config = config.copy(authMethod = value)) } },
                        optionLabel = { it.name },
                    )
                    when (monitor.config.authMethod) {
                        HttpAuthMethod.BASIC, HttpAuthMethod.NTLM -> {
                            KomaField(
                                monitor.config.basicAuthUser,
                                { value -> update { copy(config = config.copy(basicAuthUser = value)) } },
                                "Username",
                            )
                            KomaField(
                                monitor.config.basicAuthPassword,
                                { value -> update { copy(config = config.copy(basicAuthPassword = value)) } },
                                "Password",
                                password = true,
                            )
                        }
                        HttpAuthMethod.BEARER -> KomaField(
                            monitor.config.bearerToken,
                            { value -> update { copy(config = config.copy(bearerToken = value)) } },
                            "Bearer token",
                            password = true,
                        )
                        HttpAuthMethod.NONE -> Unit
                    }
                    SwitchRow(
                        "Ignore TLS errors",
                        monitor.config.ignoreTls,
                        { value -> update { copy(config = config.copy(ignoreTls = value)) } },
                        helper = "Accepts self signed and expired certificates",
                    )
                    SwitchRow(
                        "Warn before the certificate expires",
                        monitor.config.certificateExpiryNotification,
                        { value -> update { copy(config = config.copy(certificateExpiryNotification = value)) } },
                        helper = "Notifies at ${monitor.config.certificateExpiryDays.joinToString(", ")} days left",
                    )
                }
            }
        }

        item {
            Column {
                SectionTitle("Schedule")
                NumberField(
                    monitor.intervalSeconds,
                    { value -> update { copy(intervalSeconds = value) } },
                    "Check interval (seconds)",
                    min = Monitor.MIN_INTERVAL_SECONDS,
                )
                NumberField(
                    monitor.maxRetries,
                    { value -> update { copy(maxRetries = value) } },
                    "Retries before marking down",
                )
                NumberField(
                    monitor.retryIntervalSeconds,
                    { value -> update { copy(retryIntervalSeconds = value) } },
                    "Retry interval (seconds)",
                    min = Monitor.MIN_INTERVAL_SECONDS,
                )
                NumberField(
                    monitor.resendIntervalBeats,
                    { value -> update { copy(resendIntervalBeats = value) } },
                    "Resend notification every n beats",
                    helper = "0 disables repeated alerts while a monitor stays down",
                )
                NumberField(
                    monitor.timeoutSeconds,
                    { value -> update { copy(timeoutSeconds = value) } },
                    "Timeout (seconds)",
                    min = 1,
                )
                SwitchRow(
                    "Upside down mode",
                    monitor.upsideDown,
                    { value -> update { copy(upsideDown = value) } },
                    helper = "A reachable service counts as down",
                )
                SwitchRow(
                    "Active",
                    monitor.active,
                    { value -> update { copy(active = value) } },
                    helper = "Paused monitors keep their history but stop checking",
                )
            }
        }

        item {
            Column {
                SectionTitle("Placement")
                KomaDropdown(
                    label = "Parent group",
                    options = listOf<Monitor?>(null) + groups.filter { it.type == MonitorType.GROUP && it.id != monitor.id },
                    selected = groups.firstOrNull { it.id == monitor.parentId },
                    onSelect = { value -> update { copy(parentId = value?.id) } },
                    optionLabel = { it?.name ?: "No group" },
                    placeholder = "No group",
                )
                if (proxies.isNotEmpty()) {
                    KomaDropdown(
                        label = "Proxy",
                        options = listOf<ProxyServer?>(null) + proxies,
                        selected = proxies.firstOrNull { it.id == monitor.proxyId },
                        onSelect = { value -> update { copy(proxyId = value?.id) } },
                        optionLabel = { it?.let { proxy -> "${proxy.protocol}://${proxy.host}:${proxy.port}" } ?: "No proxy" },
                        placeholder = "No proxy",
                    )
                }
                KomaField(
                    monitor.description.orEmpty(),
                    { value -> update { copy(description = value.ifBlank { null }) } },
                    "Description",
                    singleLine = false,
                    minLines = 2,
                )
            }
        }

        if (allTags.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Tags")
                    allTags.forEach { tag ->
                        val assigned = monitor.tags.any { it.tagId == tag.id }
                        CheckRow(
                            label = tag.name,
                            checked = assigned,
                            onCheckedChange = { checked ->
                                update {
                                    copy(
                                        tags = if (checked) {
                                            tags + TagAssignment(tag.id, tag.name, tag.color)
                                        } else {
                                            tags.filterNot { it.tagId == tag.id }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle("Notifications")
                if (channels.isEmpty()) {
                    Text(
                        "No notification channels configured yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                channels.forEach { channel ->
                    CheckRow(
                        label = channel.name,
                        secondary = channel.provider,
                        checked = channel.id in monitor.notificationIds,
                        onCheckedChange = { checked ->
                            update {
                                copy(
                                    notificationIds = if (checked) {
                                        notificationIds + channel.id
                                    } else {
                                        notificationIds - channel.id
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }

        if (errors.isNotEmpty()) {
            item { InlineMessage(errors.joinToString("\n"), error = true) }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val validation = monitor.validate()
                        errors = validation
                        if (validation.isEmpty()) {
                            scope.launch {
                                val prepared = monitor.copy(
                                    pushToken = if (monitor.type == MonitorType.PUSH) {
                                        monitor.pushToken ?: randomToken(24)
                                    } else {
                                        monitor.pushToken
                                    },
                                )
                                val id = core.monitors.save(prepared)
                                core.engine.syncMonitor(id)
                                onSaved(id)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Cancel")
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TypeSpecificFields(
    monitor: Monitor,
    dockerHosts: List<DockerHost>,
    update: ((Monitor.() -> Monitor)) -> Unit,
) {
    Column {
        when {
            monitor.type.usesHttp || monitor.type == MonitorType.WEBSOCKET ||
                monitor.type == MonitorType.RABBITMQ -> {
                KomaField(
                    monitor.config.url,
                    { value -> update { copy(config = config.copy(url = value)) } },
                    "URL",
                    placeholder = when (monitor.type) {
                        MonitorType.WEBSOCKET -> "wss://example.com/socket"
                        MonitorType.RABBITMQ -> "http://node:15672"
                        else -> "https://example.com"
                    },
                    helper = if (monitor.type == MonitorType.RABBITMQ) "Base URL of the management plugin" else "",
                )
            }
            monitor.type == MonitorType.PING || monitor.type == MonitorType.DNS -> {
                KomaField(
                    monitor.config.hostname,
                    { value -> update { copy(config = config.copy(hostname = value)) } },
                    "Hostname",
                )
            }
            monitor.type.usesHostPort -> {
                KomaField(
                    monitor.config.hostname,
                    { value -> update { copy(config = config.copy(hostname = value)) } },
                    "Hostname",
                )
                NumberField(
                    monitor.config.port,
                    { value -> update { copy(config = config.copy(port = value)) } },
                    "Port",
                )
            }
            monitor.type == MonitorType.PUSH -> {
                KomaField(
                    monitor.pushToken.orEmpty(),
                    { value -> update { copy(pushToken = value) } },
                    "Push token",
                    helper = "Your service calls /api/push/<token> at least once per interval",
                )
            }
            else -> Unit
        }

        when (monitor.type) {
            MonitorType.KEYWORD -> {
                KomaField(
                    monitor.config.keyword,
                    { value -> update { copy(config = config.copy(keyword = value)) } },
                    "Keyword",
                )
                SwitchRow(
                    "Invert keyword",
                    monitor.config.invertKeyword,
                    { value -> update { copy(config = config.copy(invertKeyword = value)) } },
                    helper = "The monitor goes down when the keyword is present",
                )
            }
            MonitorType.JSON_QUERY -> {
                KomaField(
                    monitor.config.jsonPath,
                    { value -> update { copy(config = config.copy(jsonPath = value)) } },
                    "JSON path",
                    helper = "For example $.data.status",
                )
                KomaField(
                    monitor.config.expectedValue,
                    { value -> update { copy(config = config.copy(expectedValue = value)) } },
                    "Expected value",
                )
            }
            MonitorType.DNS -> {
                KomaField(
                    monitor.config.dnsResolverServer,
                    { value -> update { copy(config = config.copy(dnsResolverServer = value)) } },
                    "Resolver server",
                )
                NumberField(
                    monitor.config.dnsResolverPort,
                    { value -> update { copy(config = config.copy(dnsResolverPort = value)) } },
                    "Resolver port",
                    min = 1,
                )
                KomaDropdown(
                    label = "Record type",
                    options = DnsRecordType.entries,
                    selected = monitor.config.dnsRecordType,
                    onSelect = { value -> update { copy(config = config.copy(dnsRecordType = value)) } },
                    optionLabel = { it.name },
                )
            }
            MonitorType.PING -> {
                NumberField(
                    monitor.config.packetSize,
                    { value -> update { copy(config = config.copy(packetSize = value)) } },
                    "Packet size",
                    min = 8,
                )
            }
            MonitorType.MQTT -> {
                KomaField(
                    monitor.config.mqttTopic,
                    { value -> update { copy(config = config.copy(mqttTopic = value)) } },
                    "Topic",
                )
                KomaField(
                    monitor.config.mqttUsername,
                    { value -> update { copy(config = config.copy(mqttUsername = value)) } },
                    "Username",
                )
                KomaField(
                    monitor.config.mqttPassword,
                    { value -> update { copy(config = config.copy(mqttPassword = value)) } },
                    "Password",
                    password = true,
                )
                KomaField(
                    monitor.config.mqttSuccessMessage,
                    { value -> update { copy(config = config.copy(mqttSuccessMessage = value)) } },
                    "Expected message",
                    helper = "Leave empty to accept any message on the topic",
                )
            }
            MonitorType.DOCKER -> {
                KomaDropdown(
                    label = "Docker host",
                    options = dockerHosts,
                    selected = dockerHosts.firstOrNull { it.id == monitor.config.dockerHostId },
                    onSelect = { value -> update { copy(config = config.copy(dockerHostId = value.id)) } },
                    optionLabel = { "${it.name} (${it.daemon})" },
                    placeholder = if (dockerHosts.isEmpty()) "Add a Docker host in settings" else "Select",
                )
                KomaField(
                    monitor.config.dockerContainer,
                    { value -> update { copy(config = config.copy(dockerContainer = value)) } },
                    "Container name",
                )
            }
            MonitorType.REDIS, MonitorType.POSTGRES, MonitorType.MYSQL, MonitorType.MONGODB -> {
                KomaField(
                    monitor.config.databaseUser,
                    { value -> update { copy(config = config.copy(databaseUser = value)) } },
                    "Database user",
                )
                KomaField(
                    monitor.config.databasePassword,
                    { value -> update { copy(config = config.copy(databasePassword = value)) } },
                    "Database password",
                    password = true,
                )
                KomaField(
                    monitor.config.databaseName,
                    { value -> update { copy(config = config.copy(databaseName = value)) } },
                    "Database name",
                )
                SwitchRow(
                    "Use TLS",
                    monitor.config.useTls,
                    { value -> update { copy(config = config.copy(useTls = value)) } },
                )
            }
            MonitorType.RADIUS -> {
                KomaField(
                    monitor.config.radiusSecret,
                    { value -> update { copy(config = config.copy(radiusSecret = value)) } },
                    "Shared secret",
                    password = true,
                )
                KomaField(
                    monitor.config.databaseUser,
                    { value -> update { copy(config = config.copy(databaseUser = value)) } },
                    "Username",
                )
                KomaField(
                    monitor.config.databasePassword,
                    { value -> update { copy(config = config.copy(databasePassword = value)) } },
                    "Password",
                    password = true,
                )
                KomaField(
                    monitor.config.radiusCalledStationId,
                    { value -> update { copy(config = config.copy(radiusCalledStationId = value)) } },
                    "Called station id",
                )
                KomaField(
                    monitor.config.radiusCallingStationId,
                    { value -> update { copy(config = config.copy(radiusCallingStationId = value)) } },
                    "Calling station id",
                )
            }
            MonitorType.SNMP -> {
                KomaField(
                    monitor.config.snmpCommunity,
                    { value -> update { copy(config = config.copy(snmpCommunity = value)) } },
                    "Community string",
                )
                KomaField(
                    monitor.config.snmpOid,
                    { value -> update { copy(config = config.copy(snmpOid = value)) } },
                    "OID",
                    placeholder = "1.3.6.1.2.1.1.3.0",
                )
                KomaField(
                    monitor.config.snmpExpectedValue,
                    { value -> update { copy(config = config.copy(snmpExpectedValue = value)) } },
                    "Expected value",
                    helper = "Leave empty to accept any value the agent returns",
                )
                SwitchRow(
                    "SNMP v2c",
                    monitor.config.snmpVersion2c,
                    { value -> update { copy(config = config.copy(snmpVersion2c = value)) } },
                    helper = "Turn off to send v1 requests",
                )
            }
            MonitorType.RABBITMQ -> {
                KomaField(
                    monitor.config.databaseUser,
                    { value -> update { copy(config = config.copy(databaseUser = value)) } },
                    "Management user",
                )
                KomaField(
                    monitor.config.databasePassword,
                    { value -> update { copy(config = config.copy(databasePassword = value)) } },
                    "Management password",
                    password = true,
                )
            }
            MonitorType.MANUAL -> {
                Text(
                    "A manual monitor keeps the status you set on its detail screen, for services the engine cannot reach itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            MonitorType.GROUP -> {
                Text(
                    "A group aggregates its children: it reports down as soon as one child is down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            else -> Unit
        }
    }
}

private fun parseHeaders(text: String): Map<String, String> = text.lines()
    .mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@mapNotNull null
        val name = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        if (name.isEmpty()) null else name to value
    }
    .toMap()
