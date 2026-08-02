package dev.haasele.koma.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.AppSession
import dev.haasele.koma.app.Session
import dev.haasele.koma.app.theme.DefaultAccentHex
import dev.haasele.koma.app.theme.ThemeIds
import dev.haasele.koma.app.theme.accentFromHue
import dev.haasele.koma.app.theme.hueDegrees
import dev.haasele.koma.app.theme.parseAccent
import dev.haasele.koma.app.theme.toHexRgb
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaDropdown
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.NumberField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.SwitchRow
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.Autostart
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.PlatformKind
import dev.haasele.koma.shared.core.openAppNotificationSettings
import dev.haasele.koma.shared.crypto.Totp
import dev.haasele.koma.shared.crypto.randomToken
import dev.haasele.koma.shared.domain.AppSettings
import dev.haasele.koma.shared.domain.DockerConnectionType
import dev.haasele.koma.shared.domain.DockerHost
import dev.haasele.koma.shared.domain.ProxyServer
import dev.haasele.koma.shared.domain.Tag
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun SettingsScreen(core: KomaCore, session: AppSession, onOpenRemoteConsole: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by core.settings.observe().collectAsState(AppSettings())
    val running by core.engine.running.collectAsState()
    val tags by core.tags.observeAll().collectAsState(emptyList())
    val proxies by core.infrastructure.observeProxies().collectAsState(emptyList())
    val dockerHosts by core.infrastructure.observeDockerHosts().collectAsState(emptyList())
    val apiKeys by core.infrastructure.observeApiKeys().collectAsState(emptyList())

    var feedback by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(settings) {
        if (draft == null) draft = settings
    }

    val current = draft ?: return
    fun update(block: AppSettings.() -> AppSettings) {
        val updated = current.block()
        draft = updated
        scope.launch { core.settings.save(updated) }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column {
                Text(
                    "Settings",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                )
                Text(
                    "${Platform.name} · engine ${if (running) "running" else "stopped"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InlineMessage(feedback)
            }
        }

        item {
            Column {
                SectionTitle("Engine")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { scope.launch { if (running) core.engine.stop() else core.engine.start() } },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(if (running) "Stop engine" else "Start engine")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                core.monitors.getActive().forEach { core.engine.syncMonitor(it.id) }
                                feedback = "All active monitors rescheduled"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Reschedule all")
                    }
                }
                SwitchRow(
                    "Start the engine when the app launches",
                    current.startEngineOnLaunch,
                    { value -> update { copy(startEngineOnLaunch = value) } },
                )
                if (Autostart.supported) {
                    var autostart by remember { mutableStateOf(Autostart.isEnabled()) }
                    SwitchRow(
                        "Launch Uptime Koma when you sign in",
                        autostart,
                        { value ->
                            val applied = Autostart.setEnabled(value)
                            autostart = if (applied) value else Autostart.isEnabled()
                            if (!applied) feedback = "The autostart entry could not be written"
                        },
                        helper = "Keeps checks running across reboots without opening the window",
                    )
                }
                NumberField(
                    current.keepHeartbeatDays,
                    { value -> update { copy(keepHeartbeatDays = value) } },
                    "Keep heartbeats for (days)",
                    helper = "0 keeps everything; pruning runs every six hours",
                )
                if (Platform.kind == PlatformKind.ANDROID) {
                    SectionTitle("Notifications")
                    Text(
                        "Background monitoring and monitor alerts use separate Android notification " +
                            "categories, so you can mute the persistent status icon without silencing up/down alerts — or the other way around.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openAppNotificationSettings() },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Open notification settings")
                    }
                }
                if (!Platform.supportsLongRunningEngine) {
                    Text(
                        "This platform suspends background work aggressively. Checks run while the app is " +
                            "in the foreground; use a desktop instance for continuous monitoring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!Platform.supportsIcmp) {
                    Text(
                        "Raw ICMP is unavailable here, so ping monitors fall back to a TCP reachability probe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column {
                SectionTitle("Design")
                KomaDropdown(
                    label = "Theme",
                    options = ThemeIds.all,
                    selected = ThemeIds.normalize(current.theme),
                    onSelect = { value -> update { copy(theme = value) } },
                    optionLabel = ThemeIds::label,
                )
                AccentColorPicker(
                    hex = current.accentColor,
                    onChange = { value -> update { copy(accentColor = value) } },
                )
            }
        }

        if (Platform.supportsEmbeddedServer) {
            item {
                val cliManaged = core.cliManagedServer
                Column {
                    SectionTitle("Embedded server")
                    if (cliManaged != null) {
                        Text(
                            "Managed by the command line (--http / --https). " +
                                "Toggle this section in Settings is disabled to avoid a second listener.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Exposed as",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        cliManaged.publicUrls().forEach { url ->
                            Text(url, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        SwitchRow(
                            "Accept push monitors and metrics",
                            current.embeddedServerEnabled,
                            { value ->
                                update { copy(embeddedServerEnabled = value) }
                                scope.launch {
                                    if (value) {
                                        core.embeddedServer.start(current.embeddedServerPort)
                                    } else {
                                        core.embeddedServer.stop()
                                    }
                                }
                            },
                            helper = "Serves /api/push, /metrics and the status screen JSON",
                        )
                        NumberField(
                            current.embeddedServerPort,
                            { value ->
                                update { copy(embeddedServerPort = value) }
                                if (current.embeddedServerEnabled) {
                                    scope.launch {
                                        core.embeddedServer.stop()
                                        core.embeddedServer.start(value)
                                    }
                                }
                            },
                            "Port",
                        )
                        SwitchRow(
                            "Expose Prometheus metrics",
                            current.metricsEnabled,
                            { value -> update { copy(metricsEnabled = value) } },
                        )
                        SwitchRow(
                            "Allow remote UI clients",
                            current.remoteAccessEnabled,
                            { value ->
                                val token = current.remoteAccessToken.ifBlank { randomToken(32) }
                                update { copy(remoteAccessEnabled = value, remoteAccessToken = token) }
                            },
                            helper = "Mobile devices can drive this instance over the WebSocket protocol",
                        )
                        if (current.remoteAccessEnabled) {
                            KomaField(
                                current.remoteAccessToken,
                                { value -> update { copy(remoteAccessToken = value) } },
                                "Remote access token",
                                helper = "Pair a device by entering this token there",
                            )
                        }
                        if (current.embeddedServerEnabled) {
                            Spacer(Modifier.height(10.dp))
                            val urls = remember(
                                current.embeddedServerEnabled,
                                current.embeddedServerPort,
                                core.embeddedServer.isRunning,
                                core.embeddedServer.port,
                            ) {
                                core.embeddedServer.advertisedUrls()
                            }
                            Text(
                                "Exposed as",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (urls.isEmpty()) {
                                Text(
                                    "Starting… (or port ${current.embeddedServerPort} is busy)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                urls.forEach { url ->
                                    Text(url, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Text(
                                "Use one of these hostnames from other devices on your network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle("Remote instance")
                Text(
                    "Control an engine that runs on another machine, for example a desktop that stays on around the clock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenRemoteConsole, shape = RoundedCornerShape(10.dp)) {
                    Text("Open remote console")
                }
            }
        }

        item { AccountSection(core, session) { feedback = it } }

        item { TagSection(core, tags) }

        item { ProxySection(core, proxies) }

        item { DockerSection(core, dockerHosts) }

        item {
            Column {
                SectionTitle("API keys")
                apiKeys.forEach { key ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${key.prefix}… ${if (key.active) "" else "· disabled"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { scope.launch { core.infrastructure.deleteApiKey(key.id) } }) {
                            Icon(Icons.Default.Clear, contentDescription = "Delete")
                        }
                    }
                }
                var keyName by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KomaField(keyName, { keyName = it }, "New key name", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (keyName.isNotBlank()) {
                            scope.launch {
                                val secret = core.infrastructure.createApiKey(keyName, null)
                                feedback = "Copy this key now, it is shown once: $secret"
                                keyName = ""
                            }
                        }
                    }) {
                        Text("Create")
                    }
                }
            }
        }

        item { BackupSection(core) { feedback = it } }

        item {
            Column(Modifier.padding(vertical = 24.dp)) {
                TextButton(onClick = { session.logout() }) { Text("Sign out") }
                Text(
                    "Uptime Koma · a Kotlin Multiplatform rewrite of the Uptime Koma product model. " +
                        "No Node.js, no web view.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun AccountSection(core: KomaCore, session: AppSession, onFeedback: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val user = (session.state as? Session.Authenticated)?.user ?: return

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(user.username) }
    var totpSecret by remember { mutableStateOf<String?>(null) }
    var totpCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column {
        SectionTitle("Account")
        KomaField(username, { username = it }, "Username")
        TextButton(onClick = {
            scope.launch {
                core.users.changeUsername(user.id, username.trim())
                session.reloadUser()
                onFeedback("Username updated")
            }
        }) {
            Text("Save username")
        }

        KomaField(currentPassword, { currentPassword = it }, "Current password", password = true)
        KomaField(newPassword, { newPassword = it }, "New password", password = true)
        TextButton(onClick = {
            when {
                newPassword.length < 8 -> error = "The new password needs at least 8 characters"
                else -> scope.launch {
                    val changed = core.users.changePassword(user.id, currentPassword, newPassword)
                    error = if (changed) "" else "The current password is wrong"
                    if (changed) {
                        currentPassword = ""
                        newPassword = ""
                        onFeedback("Password updated")
                    }
                }
            }
        }) {
            Text("Change password")
        }

        if (session.biometricAvailable) {
            SectionTitle("Device unlock")
            var biometricEnabled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { biometricEnabled = session.isBiometricUnlockEnabled() }
            SwitchRow(
                label = "Unlock with biometrics",
                checked = biometricEnabled,
                onCheckedChange = { enabled ->
                    biometricEnabled = enabled
                    session.setBiometricUnlockEnabled(enabled)
                },
                helper = "After sign-in, reopen the app with fingerprint or face instead of the password",
            )
        }

        SectionTitle("Two factor authentication")
        if (user.twoFactorEnabled) {
            Text(
                "Two factor authentication is active for this account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                scope.launch {
                    core.users.setTwoFactor(user.id, null, false)
                    session.reloadUser()
                    onFeedback("Two factor authentication disabled")
                }
            }) {
                Text("Disable", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val secret = totpSecret
            if (secret == null) {
                TextButton(onClick = { totpSecret = Totp.generateSecret() }) { Text("Set up") }
            } else {
                Text(
                    "Add this secret to your authenticator, then confirm with the current code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(secret, style = MaterialTheme.typography.titleMedium)
                Text(
                    Totp.provisioningUri(secret, user.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KomaField(totpCode, { totpCode = it }, "Code from your app", numeric = true)
                TextButton(onClick = {
                    val seconds = Clock.System.now().epochSeconds
                    if (Totp.verify(secret, totpCode.trim(), seconds)) {
                        scope.launch {
                            core.users.setTwoFactor(user.id, secret, true)
                            session.reloadUser()
                            totpSecret = null
                            totpCode = ""
                            error = ""
                            onFeedback("Two factor authentication enabled")
                        }
                    } else {
                        error = "That code does not match"
                    }
                }) {
                    Text("Confirm")
                }
            }
        }
        InlineMessage(error, error = true)
    }
}

@Composable
private fun TagSection(core: KomaCore, tags: List<Tag>) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("#2DD4A7") }

    Column {
        SectionTitle("Tags")
        tags.forEach { tag ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    tag.color,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { scope.launch { core.tags.delete(tag.id) } }) {
                    Icon(Icons.Default.Clear, contentDescription = "Delete tag")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KomaField(name, { name = it }, "Tag name", modifier = Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            KomaField(color, { color = it }, "Colour", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        core.tags.save(Tag(name = name.trim(), color = color.trim()))
                        name = ""
                    }
                }
            }) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun ProxySection(core: KomaCore, proxies: List<ProxyServer>) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ProxyServer()) }

    Column {
        SectionTitle("Proxies")
        proxies.forEach { proxy ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${proxy.protocol}://${proxy.host}:${proxy.port}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            if (proxy.isDefault) append("default · ")
                            append(if (proxy.active) "active" else "disabled")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scope.launch { core.infrastructure.deleteProxy(proxy.id) } }) {
                    Icon(Icons.Default.Clear, contentDescription = "Delete proxy")
                }
            }
        }
        KomaDropdown(
            label = "Protocol",
            options = listOf("http", "https", "socks5", "socks4"),
            selected = draft.protocol,
            onSelect = { draft = draft.copy(protocol = it) },
            optionLabel = { it },
        )
        KomaField(draft.host, { draft = draft.copy(host = it) }, "Host")
        NumberField(draft.port, { draft = draft.copy(port = it) }, "Port")
        KomaField(draft.username.orEmpty(), { draft = draft.copy(username = it.ifBlank { null }) }, "Username")
        KomaField(
            draft.password.orEmpty(),
            { draft = draft.copy(password = it.ifBlank { null }) },
            "Password",
            password = true,
        )
        SwitchRow("Default for new monitors", draft.isDefault, { draft = draft.copy(isDefault = it) })
        TextButton(onClick = {
            if (draft.host.isNotBlank()) {
                scope.launch {
                    core.infrastructure.saveProxy(draft)
                    draft = ProxyServer()
                }
            }
        }) {
            Text("Add proxy")
        }
    }
}

@Composable
private fun DockerSection(core: KomaCore, hosts: List<DockerHost>) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(DockerHost()) }

    Column {
        SectionTitle("Docker hosts")
        hosts.forEach { host ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(host.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${host.connectionType.name.lowercase()} · ${host.daemon}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scope.launch { core.infrastructure.deleteDockerHost(host.id) } }) {
                    Icon(Icons.Default.Clear, contentDescription = "Delete host")
                }
            }
        }
        KomaField(draft.name, { draft = draft.copy(name = it) }, "Name")
        KomaDropdown(
            label = "Connection",
            options = DockerConnectionType.entries,
            selected = draft.connectionType,
            onSelect = { draft = draft.copy(connectionType = it) },
            optionLabel = { it.name.lowercase() },
        )
        KomaField(
            draft.daemon,
            { draft = draft.copy(daemon = it) },
            "Daemon",
            helper = "Socket path or host:port",
        )
        TextButton(onClick = {
            if (draft.name.isNotBlank()) {
                scope.launch {
                    core.infrastructure.saveDockerHost(draft)
                    draft = DockerHost()
                }
            }
        }) {
            Text("Add host")
        }
    }
}

@Composable
private fun BackupSection(core: KomaCore, onFeedback: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var exportText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column {
        SectionTitle("Backup")
        Text(
            "Export monitors, notifications, status screens, tags and maintenance windows as JSON. Heartbeats stay on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        exportText = core.backup.exportJson()
                        onFeedback("Export ready · ${exportText.length} characters")
                    }
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Export")
            }
            OutlinedButton(
                onClick = {
                    if (importText.isBlank()) {
                        error = "Paste an export below first"
                        return@OutlinedButton
                    }
                    scope.launch {
                        runCatching { core.backup.importJson(importText) }
                            .onSuccess { report ->
                                error = ""
                                importText = ""
                                onFeedback(
                                    "Imported ${report.monitors} monitors, ${report.notifications} channels, " +
                                        "${report.statusPages} status screens",
                                )
                            }
                            .onFailure { error = it.message ?: "Import failed" }
                    }
                },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Import")
            }
        }
        if (exportText.isNotBlank()) {
            KomaField(
                exportText,
                { exportText = it },
                "Exported JSON",
                singleLine = false,
                minLines = 4,
            )
        }
        KomaField(
            importText,
            { importText = it },
            "Paste JSON to import",
            singleLine = false,
            minLines = 3,
        )
        if (error.isNotBlank()) InlineMessage(error, error = true)
    }
}

private val AccentPresets = listOf(
    "#2DD4A7", // default emerald
    "#3B82F6",
    "#8B5CF6",
    "#EC4899",
    "#F59E0B",
    "#EF4444",
    "#14B8A6",
    "#0EA5E9",
    "#84CC16",
    "#64748B",
)

@Composable
private fun AccentColorPicker(hex: String, onChange: (String) -> Unit) {
    val selected = remember(hex) { parseAccent(hex) }
    var hue by remember(hex) { mutableFloatStateOf(selected.hueDegrees()) }
    var hexDraft by remember(hex) { mutableStateOf(normalizeAccentHex(hex)) }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "Accent colour",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentPresets.forEach { preset ->
                val color = parseAccent(preset)
                val active = normalizeAccentHex(hex).equals(preset, ignoreCase = true)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable {
                            hexDraft = preset
                            hue = color.hueDegrees()
                            onChange(preset)
                        },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000),
                        ),
                    ),
                ),
        )
        Slider(
            value = hue,
            onValueChange = { value ->
                hue = value
                val next = accentFromHue(value).toHexRgb()
                hexDraft = next
                onChange(next)
            },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(selected)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            )
            KomaField(
                value = hexDraft,
                onValueChange = { input ->
                    hexDraft = input
                    val normalized = normalizeAccentHex(input)
                    if (normalized.length == 7) {
                        hue = parseAccent(normalized).hueDegrees()
                        onChange(normalized)
                    }
                },
                label = "Custom (#RRGGBB)",
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    hexDraft = DefaultAccentHex
                    hue = parseAccent(DefaultAccentHex).hueDegrees()
                    onChange(DefaultAccentHex)
                },
            ) {
                Text("Reset")
            }
        }
        Text(
            "Used as primary colour, and as the Material You seed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun normalizeAccentHex(value: String): String {
    val cleaned = value.trim().removePrefix("#").uppercase()
    return if (cleaned.length == 6 && cleaned.all { it in "0123456789ABCDEF" }) {
        "#$cleaned"
    } else {
        value.trim().let { if (it.startsWith("#")) it else "#$it" }
    }
}
