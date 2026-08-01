package dev.haasele.koma.app.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.haasele.koma.app.ui.CheckRow
import dev.haasele.koma.app.ui.ClickableRow
import dev.haasele.koma.app.ui.ConfirmDialog
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaDropdown
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.NumberField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.asInputTimestamp
import dev.haasele.koma.app.ui.parseInputTimestamp
import dev.haasele.koma.app.ui.weekdayName
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.MaintenanceStrategy
import dev.haasele.koma.shared.engine.MaintenanceEvaluator
import kotlinx.coroutines.launch

@Composable
fun MaintenanceScreen(core: KomaCore) {
    val maintenances by core.maintenances.observeAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Maintenance?>(null) }
    var pendingDelete by remember { mutableStateOf<Maintenance?>(null) }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete ${entry.title}?",
            message = "Monitors linked to this window resume normal alerting immediately.",
            onConfirm = { scope.launch { core.maintenances.delete(entry.id) } },
            onDismiss = { pendingDelete = null },
        )
    }

    editing?.let { entry ->
        MaintenanceEditor(
            core = core,
            maintenance = entry,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    core.maintenances.save(updated)
                    editing = null
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(
                        "Maintenance",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        "Planned downtime keeps heartbeats blue and silences alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (maintenances.isEmpty()) {
                item {
                    EmptyState(
                        title = "No maintenance windows",
                        message = "Schedule one before your next deployment.",
                        actionLabel = "Add window",
                        onAction = { editing = Maintenance() },
                    )
                }
            }

            items(maintenances, key = { it.id }) { entry ->
                val underMaintenance = MaintenanceEvaluator.isUnderMaintenance(entry, nowMs())
                ClickableRow(onClick = { editing = entry }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(entry.strategy.label)
                                    append(" · ").append(entry.monitorIds.size).append(" monitors")
                                    if (underMaintenance) append(" · active now")
                                    if (!entry.active) append(" · disabled")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.strategy == MaintenanceStrategy.MANUAL) {
                            Switch(
                                checked = entry.manualActive,
                                onCheckedChange = { checked ->
                                    scope.launch { core.maintenances.setManualActive(entry.id, checked) }
                                },
                            )
                        }
                        TextButton(onClick = { pendingDelete = entry }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = Maintenance() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New window") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun MaintenanceEditor(
    core: KomaCore,
    maintenance: Maintenance,
    onDismiss: () -> Unit,
    onSave: (Maintenance) -> Unit,
) {
    val monitors by core.monitors.observeAll().collectAsState(emptyList())
    val pages by core.statusPages.observeAll().collectAsState(emptyList())

    var draft by remember(maintenance.id) { mutableStateOf(maintenance) }
    var startText by remember(maintenance.id) { mutableStateOf(maintenance.startMs?.asInputTimestamp().orEmpty()) }
    var endText by remember(maintenance.id) { mutableStateOf(maintenance.endMs?.asInputTimestamp().orEmpty()) }
    var error by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                if (maintenance.id == 0L) "New maintenance window" else "Edit maintenance window",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
        }

        item {
            Column {
                KomaField(draft.title, { draft = draft.copy(title = it) }, "Title")
                KomaField(
                    draft.description.orEmpty(),
                    { draft = draft.copy(description = it.ifBlank { null }) },
                    "Description",
                    singleLine = false,
                    minLines = 2,
                )
                KomaDropdown(
                    label = "Strategy",
                    options = MaintenanceStrategy.entries,
                    selected = draft.strategy,
                    onSelect = { draft = draft.copy(strategy = it) },
                    optionLabel = { it.label },
                )
            }
        }

        item {
            Column {
                when (draft.strategy) {
                    MaintenanceStrategy.MANUAL -> {
                        dev.haasele.koma.app.ui.SwitchRow(
                            "Currently under maintenance",
                            draft.manualActive,
                            { draft = draft.copy(manualActive = it) },
                        )
                    }
                    MaintenanceStrategy.SINGLE -> {
                        KomaField(startText, { startText = it }, "Start", helper = "yyyy-MM-dd HH:mm")
                        KomaField(endText, { endText = it }, "End", helper = "yyyy-MM-dd HH:mm")
                    }
                    MaintenanceStrategy.RECURRING_INTERVAL -> {
                        NumberField(
                            draft.intervalDay ?: 1,
                            { draft = draft.copy(intervalDay = it) },
                            "Every n days",
                            min = 1,
                        )
                        TimeWindowFields(draft) { draft = it }
                    }
                    MaintenanceStrategy.RECURRING_WEEKDAY -> {
                        SectionTitle("Weekdays")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = day in draft.weekdays,
                                    onClick = {
                                        draft = draft.copy(
                                            weekdays = if (day in draft.weekdays) {
                                                draft.weekdays - day
                                            } else {
                                                draft.weekdays + day
                                            },
                                        )
                                    },
                                    label = { Text(weekdayName(day), style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        TimeWindowFields(draft) { draft = it }
                    }
                    MaintenanceStrategy.RECURRING_DAY_OF_MONTH -> {
                        KomaField(
                            draft.daysOfMonth.joinToString(", "),
                            { value ->
                                draft = draft.copy(
                                    daysOfMonth = value.split(',').mapNotNull { it.trim().toIntOrNull() },
                                )
                            },
                            "Days of month",
                            helper = "Comma separated, for example 1, 15",
                        )
                        TimeWindowFields(draft) { draft = it }
                    }
                    MaintenanceStrategy.CRON -> {
                        KomaField(
                            draft.cron.orEmpty(),
                            { draft = draft.copy(cron = it.ifBlank { null }) },
                            "Cron expression",
                            helper = "Five fields: minute hour day month weekday",
                        )
                        NumberField(
                            draft.durationMinutes ?: 60,
                            { draft = draft.copy(durationMinutes = it) },
                            "Duration (minutes)",
                            min = 1,
                        )
                    }
                }
                dev.haasele.koma.app.ui.SwitchRow(
                    "Enabled",
                    draft.active,
                    { draft = draft.copy(active = it) },
                )
            }
        }

        item {
            Column {
                SectionTitle("Affected monitors")
                monitors.forEach { monitor ->
                    CheckRow(
                        label = monitor.name,
                        secondary = monitor.displayTarget,
                        checked = monitor.id in draft.monitorIds,
                        onCheckedChange = { checked ->
                            draft = draft.copy(
                                monitorIds = if (checked) draft.monitorIds + monitor.id else draft.monitorIds - monitor.id,
                            )
                        },
                    )
                }
            }
        }

        if (pages.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Shown on status screens")
                    pages.forEach { page ->
                        CheckRow(
                            label = page.title.ifBlank { page.slug },
                            checked = page.id in draft.statusPageIds,
                            onCheckedChange = { checked ->
                                draft = draft.copy(
                                    statusPageIds = if (checked) {
                                        draft.statusPageIds + page.id
                                    } else {
                                        draft.statusPageIds - page.id
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            Column {
                InlineMessage(error, error = true)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val startMs = startText.parseInputTimestamp()
                            val endMs = endText.parseInputTimestamp()
                            error = when {
                                draft.title.isBlank() -> "The window needs a title"
                                draft.strategy == MaintenanceStrategy.SINGLE && (startMs == null || endMs == null) ->
                                    "Start and end need the format yyyy-MM-dd HH:mm"
                                draft.strategy == MaintenanceStrategy.SINGLE && endMs!! <= startMs!! ->
                                    "The end must be after the start"
                                else -> ""
                            }
                            if (error.isEmpty()) onSave(draft.copy(startMs = startMs, endMs = endMs))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
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
}

@Composable
private fun TimeWindowFields(draft: Maintenance, onChange: (Maintenance) -> Unit) {
    KomaField(
        draft.startTime.orEmpty(),
        { onChange(draft.copy(startTime = it.ifBlank { null })) },
        "Daily start time",
        helper = "HH:mm in local time",
    )
    KomaField(
        draft.endTime.orEmpty(),
        { onChange(draft.copy(endTime = it.ifBlank { null })) },
        "Daily end time",
        helper = "HH:mm, may cross midnight",
    )
}
