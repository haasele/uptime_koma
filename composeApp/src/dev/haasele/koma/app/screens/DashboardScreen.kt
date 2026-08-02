package dev.haasele.koma.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.theme.KomaMotion
import dev.haasele.koma.app.theme.LocalStatusPalette
import dev.haasele.koma.app.ui.ClickableRow
import dev.haasele.koma.app.ui.DailyUptimeChart
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.HeartbeatBar
import dev.haasele.koma.app.ui.LatencySparkline
import dev.haasele.koma.app.ui.MetricSpec
import dev.haasele.koma.app.ui.ResponsiveMetricRow
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.StatusDot
import dev.haasele.koma.app.ui.StatusMixBar
import dev.haasele.koma.app.ui.asMillis
import dev.haasele.koma.app.ui.asPercent
import dev.haasele.koma.app.ui.label
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.relativeToNow
import dev.haasele.koma.shared.data.DailyUptime
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.UptimeStats
import kotlinx.coroutines.launch

private data class MonitorPulse(
    val monitor: Monitor,
    val beats: List<Heartbeat>,
    val stats: UptimeStats?,
)

private data class DashboardEvent(
    val monitor: Monitor,
    val beat: Heartbeat,
)

private data class DashboardInsights(
    val avgUptime24h: Double? = null,
    val avgUptime30d: Double? = null,
    val avgPing24h: Double? = null,
    val fleetDaily: List<DailyUptime> = emptyList(),
    val fleetPing: List<Float> = emptyList(),
    val pulse: List<MonitorPulse> = emptyList(),
    val slowest: List<MonitorPulse> = emptyList(),
    val weakest: List<MonitorPulse> = emptyList(),
    val recentEvents: List<DashboardEvent> = emptyList(),
)

/**
 * Fleet overview: health mix, availability / latency charts, pulse strips and attention items.
 */
@Composable
fun DashboardScreen(
    core: KomaCore,
    onOpenMonitor: (Long) -> Unit,
    onOpenServices: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val monitors by core.monitors.observeAll().collectAsState(emptyList())
    val latest by core.heartbeats.observeLatestPerMonitor().collectAsState(emptyMap())
    val running by core.engine.running.collectAsState()
    val scope = rememberCoroutineScope()
    val palette = LocalStatusPalette.current

    val active = monitors.filter { it.active }
    val up = active.count { latest[it.id]?.status == MonitorStatus.UP }
    val down = active.count { latest[it.id]?.status == MonitorStatus.DOWN }
    val pending = active.count {
        val status = latest[it.id]?.status
        status == null || status == MonitorStatus.PENDING
    }
    val maintenance = active.count { latest[it.id]?.status == MonitorStatus.MAINTENANCE }
    val paused = monitors.count { !it.active }
    val attention = active.filter { latest[it.id]?.status == MonitorStatus.DOWN }
        .sortedByDescending { latest[it.id]?.timeMs ?: 0L }

    var insights by remember { mutableStateOf(DashboardInsights()) }
    var pulseSlots by remember { mutableStateOf(0) }
    val monitorKey = monitors.joinToString(",") { "${it.id}:${it.active}" }
    val latestKey = latest.entries.joinToString(",") { "${it.key}:${it.value.status.code}:${it.value.timeMs}" }

    LaunchedEffect(monitorKey, latestKey) {
        insights = loadDashboardInsights(core, monitors)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val headline = when {
                        monitors.isEmpty() -> "Ready when you are"
                        down > 0 -> "$down down"
                        else -> "All systems up"
                    }
                    AnimatedContent(
                        targetState = headline,
                        transitionSpec = { KomaMotion.contentCrossfade() },
                        label = "dashboard-headline",
                        modifier = Modifier.weight(1f),
                    ) { text ->
                        Text(
                            text = text,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.8).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { if (running) core.engine.stop() else core.engine.start() }
                    }) {
                        Icon(
                            imageVector = if (running) KomaIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (running) "Pause the engine" else "Start the engine",
                            tint = if (running) palette.up else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(MonitorStatus.UP, active = running, size = 8)
                    Spacer(Modifier.width(8.dp))
                    val subtitle = buildString {
                        append(if (running) "Engine running" else "Engine paused")
                        append(" · ${active.size} active")
                        insights.avgUptime24h?.let { append(" · ${it.asPercent(1)} / 24h") }
                    }
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { KomaMotion.contentCrossfade() },
                        label = "dashboard-subtitle",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (monitors.isEmpty()) {
            item {
                EmptyState(
                    title = "No services yet",
                    message = "Add checks under Services. The dashboard will fill with uptime charts, latency trends and outages.",
                    actionLabel = "Open Services",
                    onAction = onOpenServices,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        item {
            SectionTitle("Overview")
            ResponsiveMetricRow(
                listOf(
                    MetricSpec("Up", up.toString(), palette.up),
                    MetricSpec("Down", down.toString(), if (down > 0) palette.down else null),
                    MetricSpec(
                        "Uptime 24h",
                        insights.avgUptime24h?.asPercent(1) ?: "—",
                        palette.up,
                    ),
                    MetricSpec("Avg latency", insights.avgPing24h.asMillis()),
                ),
            )
            Spacer(Modifier.height(14.dp))
            StatusMixBar(
                up = up,
                down = down,
                pending = pending,
                paused = paused,
                maintenance = maintenance,
            )
        }

        item {
            SectionTitle(
                text = "Fleet insights",
                trailing = {
                    TextButton(onClick = onOpenServices) { Text("Services") }
                },
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val sideBySide = maxWidth >= 860.dp
                if (sideBySide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        FleetAvailabilityColumn(
                            daily = insights.fleetDaily,
                            avg30d = insights.avgUptime30d,
                            modifier = Modifier.weight(1f),
                        )
                        FleetLatencyColumn(
                            points = insights.fleetPing,
                            avgPing = insights.avgPing24h,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        FleetAvailabilityColumn(
                            daily = insights.fleetDaily,
                            avg30d = insights.avgUptime30d,
                        )
                        FleetLatencyColumn(
                            points = insights.fleetPing,
                            avgPing = insights.avgPing24h,
                        )
                    }
                }
            }
        }

        if (insights.weakest.isNotEmpty() || insights.slowest.isNotEmpty()) {
            item {
                SectionTitle("Watch list")
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sideBySide = maxWidth >= 860.dp
                    if (sideBySide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            InsightListColumn(
                                title = "Lowest uptime (24h)",
                                entries = insights.weakest,
                                valueOf = { it.stats?.uptime24h?.asPercent(1) ?: "—" },
                                accent = palette.down,
                                onOpen = onOpenMonitor,
                                modifier = Modifier.weight(1f),
                            )
                            InsightListColumn(
                                title = "Highest latency",
                                entries = insights.slowest,
                                valueOf = { it.stats?.avgPing24h.asMillis() },
                                onOpen = onOpenMonitor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InsightListColumn(
                                title = "Lowest uptime (24h)",
                                entries = insights.weakest,
                                valueOf = { it.stats?.uptime24h?.asPercent(1) ?: "—" },
                                accent = palette.down,
                                onOpen = onOpenMonitor,
                            )
                            InsightListColumn(
                                title = "Highest latency",
                                entries = insights.slowest,
                                valueOf = { it.stats?.avgPing24h.asMillis() },
                                onOpen = onOpenMonitor,
                            )
                        }
                    }
                }
            }
        }

        if (insights.pulse.isNotEmpty()) {
            item {
                SectionTitle(
                    text = if (pulseSlots > 0) "Live pulse · last $pulseSlots" else "Live pulse",
                    trailing = {
                        TextButton(onClick = onOpenStatus) { Text("Status screens") }
                    },
                )
            }
            items(insights.pulse, key = { "pulse-${it.monitor.id}" }) { entry ->
                PulseRow(
                    entry,
                    latest[entry.monitor.id],
                    onOpen = { onOpenMonitor(entry.monitor.id) },
                    modifier = Modifier.animateItem(),
                    onVisibleSlotsChange = { pulseSlots = it },
                )
            }
        }

        item {
            SectionTitle("Needs attention")
        }
        if (attention.isEmpty()) {
            item {
                Text(
                    "Nothing needs attention right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(attention, key = { "attn-${it.id}" }) { monitor ->
                AttentionRow(
                    monitor,
                    latest[monitor.id],
                    onOpen = { onOpenMonitor(monitor.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (insights.recentEvents.isNotEmpty()) {
            item { SectionTitle("Recent changes") }
            items(insights.recentEvents, key = { "evt-${it.beat.id}-${it.monitor.id}" }) { event ->
                EventRow(
                    event,
                    onOpen = { onOpenMonitor(event.monitor.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun FleetAvailabilityColumn(
    daily: List<DailyUptime>,
    avg30d: Double?,
    modifier: Modifier = Modifier,
) {
    var visibleDays by remember { mutableStateOf(daily.size.coerceAtLeast(1)) }
    Column(modifier) {
        Text(
            "Availability · $visibleDays days",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = avg30d?.let { "Fleet average ${it.asPercent(1)}" }
                ?: "Aggregated across active checks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        DailyUptimeChart(
            days = daily,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            onVisibleDaysChange = { visibleDays = it },
        )
        Spacer(Modifier.height(6.dp))
        val shown = daily.takeLast(visibleDays)
        val badDays = shown.count { it.down > 0 }
        Text(
            text = when {
                shown.none { it.hasData } -> "Waiting for the first daily rollups"
                badDays == 0 -> "No day with a failed check"
                else -> "$badDays days with at least one failure"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FleetLatencyColumn(
    points: List<Float>,
    avgPing: Double?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "Response time",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = avgPing?.let { "Mean ${it.asMillis()} over the last day" }
                ?: "Mean latency across recent checks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        LatencySparkline(points, Modifier.fillMaxWidth().height(88.dp))
        if (points.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${points.min().toInt()} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${points.last().toInt()} ms now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${points.max().toInt()} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightListColumn(
    title: String,
    entries: List<MonitorPulse>,
    valueOf: (MonitorPulse) -> String,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                "Not enough history yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                ClickableRow(
                    onClick = { onOpen(entry.monitor.id) },
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            entry.beats.lastOrNull()?.status ?: MonitorStatus.PENDING,
                            active = entry.monitor.active,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            entry.monitor.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            valueOf(entry),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent ?: MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseRow(
    entry: MonitorPulse,
    latest: Heartbeat?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibleSlotsChange: (Int) -> Unit = {},
) {
    ClickableRow(onClick = onOpen, modifier = modifier.padding(vertical = 4.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(latest?.status ?: MonitorStatus.PENDING, active = entry.monitor.active)
                Spacer(Modifier.width(10.dp))
                Text(
                    entry.monitor.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = latest?.pingMs?.let { "$it ms" }
                        ?: entry.stats?.uptime24h?.asPercent(0)
                        ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            HeartbeatBar(
                beats = entry.beats,
                modifier = Modifier.fillMaxWidth(),
                barHeight = 18,
                active = entry.monitor.active,
                onVisibleSlotsChange = onVisibleSlotsChange,
            )
        }
    }
}

@Composable
private fun AttentionRow(
    monitor: Monitor,
    latest: Heartbeat?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableRow(onClick = onOpen, modifier = modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(latest?.status ?: MonitorStatus.DOWN, active = monitor.active)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = latest?.message?.take(80).orEmpty().ifBlank { monitor.displayTarget },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                latest?.timeMs?.relativeToNow() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(
    event: DashboardEvent,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableRow(onClick = onOpen, modifier = modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(event.beat.status, active = event.monitor.active)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.monitor.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(event.beat.status.label())
                        if (event.beat.message.isNotBlank()) {
                            append(" · ")
                            append(event.beat.message.take(72))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                event.beat.timeMs.relativeToNow(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun loadDashboardInsights(core: KomaCore, monitors: List<Monitor>): DashboardInsights {
    if (monitors.isEmpty()) return DashboardInsights()

    val sample = monitors
        .sortedWith(
            compareByDescending<Monitor> { it.active }
                .thenBy { it.name.lowercase() },
        )
        .take(MAX_INSIGHT_MONITORS)

    val pulses = sample.map { monitor ->
        val beats = core.heartbeats.recent(monitor.id, PULSE_BEATS)
        val stats = runCatching { core.uptime.statsFor(monitor.id) }.getOrNull()
        MonitorPulse(monitor, beats, stats)
    }

    val activePulses = pulses.filter { it.monitor.active }
    val statsList = activePulses.mapNotNull { it.stats }
    val avgUptime24h = statsList.map { it.uptime24h }.takeIf { it.isNotEmpty() }?.average()
    val avgUptime30d = statsList.map { it.uptime30d }.takeIf { it.isNotEmpty() }?.average()
    val avgPing24h = statsList.mapNotNull { it.avgPing24h }.takeIf { it.isNotEmpty() }?.average()

    val dailySeries = activePulses.map { core.stats.dailySeries(it.monitor.id, FLEET_DAYS) }
    val fleetDaily = mergeDailySeries(dailySeries)

    val fleetPing = averagePingSeries(activePulses.map { it.beats }, FLEET_PING_SLOTS)

    val weakest = activePulses
        .filter { it.stats != null }
        .sortedBy { it.stats!!.uptime24h }
        .take(3)

    val slowest = activePulses
        .filter { (it.stats?.avgPing24h ?: 0.0) > 0.0 }
        .sortedByDescending { it.stats!!.avgPing24h!! }
        .take(3)

    // Prefer currently down / pending monitors in the pulse strip, then fill with active ones.
    val pulse = buildList {
        val downFirst = activePulses
            .sortedByDescending { p ->
                when (p.beats.lastOrNull()?.status) {
                    MonitorStatus.DOWN -> 3
                    MonitorStatus.PENDING -> 2
                    MonitorStatus.MAINTENANCE -> 1
                    else -> 0
                }
            }
        addAll(downFirst.take(PULSE_ROWS))
    }.distinctBy { it.monitor.id }

    val recentEvents = activePulses
        .flatMap { pulse ->
            core.heartbeats.important(pulse.monitor.id, 4).map { beat ->
                DashboardEvent(pulse.monitor, beat)
            }
        }
        .sortedByDescending { it.beat.timeMs }
        .take(8)

    return DashboardInsights(
        avgUptime24h = avgUptime24h,
        avgUptime30d = avgUptime30d,
        avgPing24h = avgPing24h,
        fleetDaily = fleetDaily,
        fleetPing = fleetPing,
        pulse = pulse,
        slowest = slowest,
        weakest = weakest,
        recentEvents = recentEvents,
    )
}

private fun mergeDailySeries(series: List<List<DailyUptime>>): List<DailyUptime> {
    if (series.isEmpty()) return emptyList()
    val days = series.maxOf { it.size }
    if (days == 0) return emptyList()
    return (0 until days).map { index ->
        var up = 0L
        var down = 0L
        var maintenance = 0L
        var pingSum = 0.0
        var pingCount = 0
        var dayMs = 0L
        series.forEach { list ->
            val day = list.getOrNull(index) ?: return@forEach
            dayMs = day.dayMs
            up += day.up
            down += day.down
            maintenance += day.maintenance
            day.avgPing?.let {
                pingSum += it
                pingCount += 1
            }
        }
        DailyUptime(
            dayMs = dayMs,
            up = up,
            down = down,
            maintenance = maintenance,
            avgPing = if (pingCount > 0) pingSum / pingCount else null,
        )
    }
}

private fun averagePingSeries(beatLists: List<List<Heartbeat>>, slots: Int): List<Float> {
    if (beatLists.isEmpty()) return emptyList()
    return (0 until slots).mapNotNull { offset ->
        val values = beatLists.mapNotNull { beats ->
            val index = beats.size - slots + offset
            if (index in beats.indices) beats[index].pingMs?.toFloat() else null
        }
        if (values.isEmpty()) null else values.average().toFloat()
    }
}

private const val MAX_INSIGHT_MONITORS = 32
private const val FLEET_DAYS = 90
private const val FLEET_PING_SLOTS = 40
private const val PULSE_BEATS = 200
private const val PULSE_ROWS = 6
