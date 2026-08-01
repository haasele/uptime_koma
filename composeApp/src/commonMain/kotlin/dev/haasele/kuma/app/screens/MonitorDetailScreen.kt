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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.theme.LocalStatusPalette
import dev.haasele.koma.app.ui.ConfirmDialog
import dev.haasele.koma.app.ui.DailyUptimeChart
import dev.haasele.koma.app.ui.HeartbeatBar
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.MetricTile
import dev.haasele.koma.app.ui.PingSparkline
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.StatusBadge
import dev.haasele.koma.app.ui.asMillis
import dev.haasele.koma.app.ui.asPercent
import dev.haasele.koma.app.ui.label
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.core.formatDuration
import dev.haasele.koma.shared.core.relativeToNow
import dev.haasele.koma.shared.data.DailyUptime
import dev.haasele.koma.shared.domain.CertificateInfo
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.UptimeStats
import kotlinx.coroutines.launch

@Composable
fun MonitorDetailScreen(
    core: KomaCore,
    monitorId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val beats by core.heartbeats.observeRecent(monitorId, 100).collectAsState(emptyList())

    var monitor by remember { mutableStateOf<Monitor?>(null) }
    var stats by remember { mutableStateOf<UptimeStats?>(null) }
    var events by remember { mutableStateOf<List<Heartbeat>>(emptyList()) }
    var daily by remember { mutableStateOf<List<DailyUptime>>(emptyList()) }
    var certificate by remember { mutableStateOf<CertificateInfo?>(null) }
    var testResult by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var manualNote by remember { mutableStateOf("") }

    LaunchedEffect(monitorId, beats.size) {
        monitor = core.monitors.getById(monitorId)
        stats = core.uptime.statsFor(monitorId)
        events = core.heartbeats.important(monitorId, 30)
        daily = core.stats.dailySeries(monitorId, 30)
        certificate = core.engine.certificateFor(monitorId)
    }

    val current = monitor ?: return
    val last = beats.lastOrNull()
    val palette = LocalStatusPalette.current

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${current.name}?",
            message = "The monitor and its complete history are removed. This cannot be undone.",
            onConfirm = {
                scope.launch {
                    core.engine.removeMonitor(monitorId)
                    core.monitors.delete(monitorId)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column {
                Text(
                    current.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(last?.status ?: MonitorStatus.PENDING, current.active)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = current.displayTarget.ifBlank { current.type.label },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!current.description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        current.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {
                        scope.launch {
                            core.monitors.setActive(monitorId, !current.active)
                            core.engine.syncMonitor(monitorId)
                            monitor = core.monitors.getById(monitorId)
                        }
                    },
                    label = { Text(if (current.active) "Pause" else "Resume") },
                    leadingIcon = {
                        Icon(
                            if (current.active) KomaIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.width(16.dp),
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        testResult = "Running a check…"
                        scope.launch {
                            val result = core.engine.testCheck(current)
                            testResult = "${result.status.label()}: ${result.message}"
                        }
                    },
                    label = { Text("Test now") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(16.dp)) },
                )
                AssistChip(
                    onClick = { onEdit(monitorId) },
                    label = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.width(16.dp)) },
                )
                AssistChip(
                    onClick = { confirmDelete = true },
                    label = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.width(16.dp)) },
                )
            }
        }

        if (testResult.isNotBlank()) {
            item { InlineMessage(testResult, error = testResult.startsWith("Down")) }
        }

        if (current.type == MonitorType.MANUAL) {
            item {
                Column {
                    SectionTitle("Manual status")
                    KomaField(manualNote, { manualNote = it }, "Note", placeholder = "Why the status changed")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(MonitorStatus.UP, MonitorStatus.DOWN, MonitorStatus.MAINTENANCE).forEach { status ->
                            AssistChip(
                                onClick = {
                                    scope.launch {
                                        core.engine.setManualStatus(monitorId, status, manualNote)
                                        manualNote = ""
                                        monitor = core.monitors.getById(monitorId)
                                    }
                                },
                                label = { Text("Set ${status.label().lowercase()}") },
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle("Last 100 beats")
                HeartbeatBar(beats, Modifier.fillMaxWidth(), slots = 60, barHeight = 34, active = current.active)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = beats.firstOrNull()?.timeMs?.relativeToNow() ?: "no history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = last?.timeMs?.relativeToNow() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            val snapshot = stats
            Column {
                SectionTitle("Availability")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MetricTile("24 hours", snapshot?.uptime24h?.asPercent() ?: "—", palette.up, Modifier.weight(1f))
                    MetricTile("30 days", snapshot?.uptime30d?.asPercent() ?: "—", modifier = Modifier.weight(1f))
                    MetricTile("1 year", snapshot?.uptime1y?.asPercent() ?: "—", modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MetricTile("Avg response", snapshot?.avgPing24h.asMillis(), modifier = Modifier.weight(1f))
                    MetricTile("Current", last?.pingMs.asMillis(), modifier = Modifier.weight(1f))
                    MetricTile(
                        "Interval",
                        formatDuration(current.intervalSeconds.toLong()),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Column {
                SectionTitle("Response time")
                PingSparkline(beats, Modifier.fillMaxWidth().height(110.dp))
            }
        }

        item {
            Column {
                SectionTitle("Last 30 days")
                DailyUptimeChart(daily, Modifier.fillMaxWidth().height(70.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = daily.count { it.down > 0 }.let { badDays ->
                        if (badDays == 0) "No day with a failed check" else "$badDays days with failures"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        certificate?.let { info ->
            item {
                Column {
                    SectionTitle("TLS certificate")
                    Text(info.subject, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Issued by ${info.issuer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Expires ${info.validToMs.formatDateTime()} · ${info.daysRemaining} days left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (info.daysRemaining <= 14) palette.down else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (current.type == MonitorType.PUSH) {
            item {
                Column {
                    SectionTitle("Push endpoint")
                    Text(
                        "POST or GET  /api/push/${current.pushToken.orEmpty()}?status=up&msg=OK&ping=120",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (current.tags.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Tags")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.tags.forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag.value?.let { "${tag.name}: $it" } ?: tag.name) })
                        }
                    }
                }
            }
        }

        item { SectionTitle("Events") }

        if (events.isEmpty()) {
            item {
                Text(
                    "No status changes recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(events, key = { it.id }) { event ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${event.status.label()} · ${event.timeMs.formatDateTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.colorFor(event.status),
                    )
                    Text(
                        event.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.durationSeconds > 0) {
                    Text(
                        formatDuration(event.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
