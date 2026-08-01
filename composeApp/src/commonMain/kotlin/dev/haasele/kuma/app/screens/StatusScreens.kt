package dev.haasele.koma.app.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.theme.LocalStatusPalette
import dev.haasele.koma.app.ui.CheckRow
import dev.haasele.koma.app.ui.ClickableRow
import dev.haasele.koma.app.ui.ConfirmDialog
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.HeartbeatBar
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaDropdown
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.StatusBadge
import dev.haasele.koma.app.ui.asPercent
import dev.haasele.koma.app.ui.label
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.domain.Incident
import dev.haasele.koma.shared.domain.IncidentStyle
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.StatusPage
import dev.haasele.koma.shared.domain.StatusPageGroup
import dev.haasele.koma.shared.domain.StatusPageView
import kotlinx.coroutines.launch

@Composable
fun StatusPageListScreen(
    core: KomaCore,
    onOpen: (Long) -> Unit,
    onEdit: (Long?) -> Unit,
) {
    val pages by core.statusPages.observeAll().collectAsState(emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(
                        "Status screens",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        "Native replacements for public status pages, grouped and shareable inside the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (pages.isEmpty()) {
                item {
                    EmptyState(
                        title = "No status screens yet",
                        message = "Group your monitors into a screen your team can check at a glance.",
                        actionLabel = "Create screen",
                        onAction = { onEdit(null) },
                    )
                }
            }

            items(pages, key = { it.id }) { page ->
                ClickableRow(onClick = { onOpen(page.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(page.title.ifBlank { page.slug }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("/").append(page.slug)
                                    append(" · ").append(page.groups.sumOf { it.monitorIds.size }).append(" monitors")
                                    if (!page.published) append(" · unpublished")
                                    if (page.hasPassword) append(" · password protected")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(page.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.width(18.dp))
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onEdit(null) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New screen") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
fun StatusPageViewerScreen(core: KomaCore, pageId: Long, onEdit: (Long) -> Unit) {
    var view by remember { mutableStateOf<StatusPageView?>(null) }
    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    val palette = LocalStatusPalette.current

    LaunchedEffect(pageId) {
        view = core.statusPageService.viewById(pageId)
        incidents = core.statusPages.incidents(pageId)
    }

    val model = view ?: return
    val accent = palette.colorFor(model.overall)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.page.title.ifBlank { model.page.slug },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEdit(pageId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.width(18.dp))
                    }
                }
                model.page.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .padding(18.dp),
                ) {
                    Column {
                        Text(
                            text = when (model.overall) {
                                MonitorStatus.UP -> "All services operational"
                                MonitorStatus.DOWN -> "Service disruption"
                                MonitorStatus.MAINTENANCE -> "Maintenance in progress"
                                MonitorStatus.PENDING -> "Waiting for data"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = accent,
                        )
                        Text(
                            "${model.groups.sumOf { it.monitors.size }} monitors tracked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        model.pinnedIncident?.let { incident ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(incident.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(incident.content, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Updated ${incident.lastUpdatedAt.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (model.activeMaintenances.isNotEmpty()) {
            item {
                Column {
                    SectionTitle("Maintenance")
                    model.activeMaintenances.forEach { maintenance ->
                        Text(maintenance.title, style = MaterialTheme.typography.bodyLarge)
                        maintenance.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        model.groups.forEach { group ->
            item {
                SectionTitle(group.group.name.ifBlank { "Ungrouped" })
            }
            items(group.monitors, key = { it.monitor.id }) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.monitor.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (model.page.showUptimePercentage) {
                            Text(
                                entry.stats.uptime24h.asPercent(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        StatusBadge(entry.stats.currentStatus, entry.monitor.active)
                    }
                    Spacer(Modifier.height(8.dp))
                    HeartbeatBar(entry.recentBeats, Modifier.fillMaxWidth(), slots = 50, barHeight = 20, active = entry.monitor.active)
                }
            }
        }

        if (incidents.isNotEmpty()) {
            item { SectionTitle("Incident history") }
            items(incidents, key = { it.id }) { incident ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(incident.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        incident.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        incident.createdAt.formatDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        model.page.footerText?.takeIf { it.isNotBlank() }?.let { footer ->
            item {
                Text(
                    footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
fun StatusPageEditorScreen(
    core: KomaCore,
    pageId: Long?,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val monitors by core.monitors.observeAll().collectAsState(emptyList())

    var draft by remember { mutableStateOf<StatusPage?>(null) }
    var password by remember { mutableStateOf("") }
    var clearPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var incidentDraft by remember { mutableStateOf(Incident()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        draft = pageId?.let { core.statusPages.getById(it) } ?: StatusPage(groups = listOf(StatusPageGroup(name = "Services")))
        incidents = pageId?.let { core.statusPages.incidents(it) }.orEmpty()
    }

    val page = draft ?: return
    fun update(block: StatusPage.() -> StatusPage) {
        draft = page.block()
    }

    if (confirmDelete && pageId != null) {
        ConfirmDialog(
            title = "Delete this status screen?",
            message = "Groups and incidents are removed. The monitors themselves stay untouched.",
            onConfirm = {
                scope.launch {
                    core.statusPages.delete(pageId)
                    onCancel()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                if (pageId == null) "New status screen" else "Edit status screen",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
        }

        item {
            Column {
                KomaField(page.title, { value -> update { copy(title = value) } }, "Title")
                KomaField(
                    page.slug,
                    { value -> update { copy(slug = value.lowercase().replace(' ', '-')) } },
                    "Slug",
                    helper = "Used by the read only JSON endpoint on desktop",
                )
                KomaField(
                    page.description.orEmpty(),
                    { value -> update { copy(description = value.ifBlank { null }) } },
                    "Description",
                    singleLine = false,
                    minLines = 2,
                )
                KomaField(
                    page.footerText.orEmpty(),
                    { value -> update { copy(footerText = value.ifBlank { null }) } },
                    "Footer",
                )
                dev.haasele.koma.app.ui.SwitchRow(
                    "Published",
                    page.published,
                    { value -> update { copy(published = value) } },
                )
                dev.haasele.koma.app.ui.SwitchRow(
                    "Show uptime percentage",
                    page.showUptimePercentage,
                    { value -> update { copy(showUptimePercentage = value) } },
                )
                dev.haasele.koma.app.ui.SwitchRow(
                    "Show tags",
                    page.showTags,
                    { value -> update { copy(showTags = value) } },
                )
                KomaField(
                    password,
                    { password = it },
                    "Password",
                    password = true,
                    helper = if (page.hasPassword) "Leave empty to keep the current password" else "Optional",
                )
                if (page.hasPassword) {
                    CheckRow("Remove the password", clearPassword, { clearPassword = it })
                }
            }
        }

        item {
            SectionTitle("Groups") {
                TextButton(onClick = { update { copy(groups = groups + StatusPageGroup(name = "New group")) } }) {
                    Text("Add group")
                }
            }
        }

        itemsIndexedGroups(page.groups) { index, group ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KomaField(
                        group.name,
                        { value ->
                            update {
                                copy(groups = groups.toMutableList().also { it[index] = group.copy(name = value) })
                            }
                        },
                        "Group name",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { update { copy(groups = groups.filterIndexed { i, _ -> i != index }) } }) {
                        Icon(Icons.Default.Clear, contentDescription = "Remove group", modifier = Modifier.width(18.dp))
                    }
                }
                monitors.forEach { monitor ->
                    CheckRow(
                        label = monitor.name,
                        secondary = monitor.displayTarget,
                        checked = monitor.id in group.monitorIds,
                        onCheckedChange = { checked ->
                            val updated = if (checked) group.monitorIds + monitor.id else group.monitorIds - monitor.id
                            update {
                                copy(groups = groups.toMutableList().also { it[index] = group.copy(monitorIds = updated) })
                            }
                        },
                    )
                }
            }
        }

        if (pageId != null) {
            item {
                Column {
                    SectionTitle("Post an incident")
                    KomaField(incidentDraft.title, { incidentDraft = incidentDraft.copy(title = it) }, "Title")
                    KomaField(
                        incidentDraft.content,
                        { incidentDraft = incidentDraft.copy(content = it) },
                        "Message",
                        singleLine = false,
                        minLines = 3,
                    )
                    KomaDropdown(
                        label = "Style",
                        options = IncidentStyle.entries,
                        selected = incidentDraft.style,
                        onSelect = { incidentDraft = incidentDraft.copy(style = it) },
                        optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (incidentDraft.title.isNotBlank()) {
                                    scope.launch {
                                        core.statusPages.postIncident(
                                            incidentDraft.copy(statusPageId = pageId, pinned = true),
                                        )
                                        incidents = core.statusPages.incidents(pageId)
                                        incidentDraft = Incident()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Pin incident")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    core.statusPages.unpinIncidents(pageId)
                                    incidents = core.statusPages.incidents(pageId)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Unpin all")
                        }
                    }
                }
            }

            items(incidents, key = { it.id }) { incident ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(incident.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${if (incident.pinned) "pinned · " else ""}${incident.createdAt.formatDateTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            core.statusPages.deleteIncident(incident.id)
                            incidents = core.statusPages.incidents(pageId)
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Delete", modifier = Modifier.width(18.dp))
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            item { InlineMessage(error, error = true) }
        }

        item {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            when {
                                page.title.isBlank() -> error = "The screen needs a title"
                                page.slug.isBlank() -> error = "The screen needs a slug"
                                else -> {
                                    error = ""
                                    scope.launch {
                                        val id = core.statusPages.save(
                                            page,
                                            plainPassword = password.takeIf { it.isNotBlank() },
                                            clearPassword = clearPassword,
                                        )
                                        onSaved(id)
                                    }
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
                if (pageId != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete this screen", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** Small helper so the group editor can use an index without pulling in an extra lazy import. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedGroups(
    groups: List<StatusPageGroup>,
    content: @Composable (Int, StatusPageGroup) -> Unit,
) {
    groups.forEachIndexed { index, group ->
        item(key = "group-$index") { content(index, group) }
    }
}
