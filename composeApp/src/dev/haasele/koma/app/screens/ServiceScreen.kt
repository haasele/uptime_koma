package dev.haasele.koma.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.theme.KomaMotion
import dev.haasele.koma.app.ui.ClickableRow
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.HeartbeatBar
import dev.haasele.koma.app.ui.StatusDot
import dev.haasele.koma.app.ui.label
import dev.haasele.koma.app.ui.parseHexColor
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.relativeToNow
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.Tag
import kotlinx.coroutines.launch

private enum class StatusFilter(val label: String) {
    ALL("All"),
    UP("Up"),
    DOWN("Down"),
    PENDING("Pending"),
    PAUSED("Paused"),
}

/** Monitor list and create flow — the operational counterpart to the overview Dashboard. */
@Composable
fun ServiceScreen(
    core: KomaCore,
    onOpenMonitor: (Long) -> Unit,
    onCreateMonitor: () -> Unit,
) {
    val monitors by core.monitors.observeAll().collectAsState(emptyList())
    val latest by core.heartbeats.observeLatestPerMonitor().collectAsState(emptyMap())
    val tags by core.tags.observeAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var tagFilter by remember { mutableStateOf<Long?>(null) }
    var beatsByMonitor by remember { mutableStateOf<Map<Long, List<Heartbeat>>>(emptyMap()) }

    LaunchedEffect(monitors.size, latest) {
        beatsByMonitor = monitors.associate { it.id to core.heartbeats.recent(it.id, 200) }
    }

    val filtered = monitors.filter { monitor ->
        val beat = latest[monitor.id]
        val matchesQuery = query.isBlank() ||
            monitor.name.contains(query, ignoreCase = true) ||
            monitor.displayTarget.contains(query, ignoreCase = true)
        val matchesTag = tagFilter == null || monitor.tags.any { it.tagId == tagFilter }
        val matchesStatus = when (statusFilter) {
            StatusFilter.ALL -> true
            StatusFilter.PAUSED -> !monitor.active
            StatusFilter.UP -> monitor.active && beat?.status == MonitorStatus.UP
            StatusFilter.DOWN -> monitor.active && beat?.status == MonitorStatus.DOWN
            StatusFilter.PENDING -> monitor.active && beat?.status == MonitorStatus.PENDING
        }
        matchesQuery && matchesTag && matchesStatus
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Services",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                )
                AnimatedContent(
                    targetState = "${monitors.size} monitors",
                    transitionSpec = { KomaMotion.contentCrossfade() },
                    label = "services-count",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search monitors") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

            item {
                FilterRow(
                    statusFilter = statusFilter,
                    onStatusFilter = { statusFilter = it },
                    tags = tags,
                    tagFilter = tagFilter,
                    onTagFilter = { tagFilter = it },
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = if (monitors.isEmpty()) "No monitors yet" else "Nothing matches",
                        message = if (monitors.isEmpty()) {
                            "Add your first check and the engine starts beating right away."
                        } else {
                            "Try a different filter or search term."
                        },
                        actionLabel = if (monitors.isEmpty()) "Add monitor" else null,
                        onAction = if (monitors.isEmpty()) onCreateMonitor else null,
                    )
                }
            }

            items(filtered, key = { it.id }) { monitor ->
                MonitorRow(
                    monitor = monitor,
                    beats = beatsByMonitor[monitor.id].orEmpty(),
                    latest = latest[monitor.id],
                    onOpen = { onOpenMonitor(monitor.id) },
                    onTogglePause = {
                        scope.launch {
                            core.monitors.setActive(monitor.id, !monitor.active)
                            core.engine.syncMonitor(monitor.id)
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateMonitor,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New monitor") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun FilterRow(
    statusFilter: StatusFilter,
    onStatusFilter: (StatusFilter) -> Unit,
    tags: List<Tag>,
    tagFilter: Long?,
    onTagFilter: (Long?) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { onStatusFilter(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tagFilter == null,
                    onClick = { onTagFilter(null) },
                    label = { Text("Any tag", style = MaterialTheme.typography.labelSmall) },
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = tagFilter == tag.id,
                        onClick = { onTagFilter(if (tagFilter == tag.id) null else tag.id) },
                        label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parseHexColor(tag.color, MaterialTheme.colorScheme.primary)),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(
    monitor: Monitor,
    beats: List<Heartbeat>,
    latest: Heartbeat?,
    onOpen: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableRow(onClick = onOpen, modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(latest?.status ?: MonitorStatus.PENDING, active = monitor.active)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        monitor.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (monitor.displayTarget.isNotBlank()) {
                        Text(
                            monitor.displayTarget,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = latest?.pingMs?.let { "$it ms" } ?: monitor.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (monitor.active) KomaIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (monitor.active) "Pause" else "Resume",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HeartbeatBar(beats, Modifier.fillMaxWidth(), barHeight = 22, active = monitor.active)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = latest?.message?.take(60).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = latest?.timeMs?.relativeToNow() ?: "never checked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
