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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.haasele.koma.app.ui.ClickableRow
import dev.haasele.koma.app.ui.ConfirmDialog
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaDropdown
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.SwitchRow
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.domain.ConfigFieldType
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.notify.NotificationProvider
import dev.haasele.koma.shared.notify.NotificationRegistry
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(core: KomaCore) {
    val channels by core.notifications.observeAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<NotificationChannel?>(null) }
    var feedback by remember { mutableStateOf("") }
    var feedbackIsError by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NotificationChannel?>(null) }

    pendingDelete?.let { channel ->
        ConfirmDialog(
            title = "Delete ${channel.name}?",
            message = "The channel is detached from every monitor that uses it.",
            onConfirm = { scope.launch { core.notifications.delete(channel.id) } },
            onDismiss = { pendingDelete = null },
        )
    }

    editing?.let { channel ->
        ChannelEditor(
            channel = channel,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    core.notifications.save(updated)
                    editing = null
                }
            },
            onTest = { candidate ->
                scope.launch {
                    val result = core.notificationDispatcher.test(candidate)
                    feedbackIsError = result.isFailure
                    feedback = result.fold(
                        onSuccess = { "Test notification sent through ${candidate.name}" },
                        onFailure = { "Test failed: ${it.message}" },
                    )
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
                        "Notifications",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        "${NotificationRegistry.all.size} transports available, from the local device to PagerDuty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InlineMessage(feedback, feedbackIsError)
                }
            }

            if (channels.isEmpty()) {
                item {
                    EmptyState(
                        title = "No channels yet",
                        message = "Add a channel and attach it to any monitor from its editor.",
                        actionLabel = "Add channel",
                        onAction = { editing = NotificationChannel() },
                    )
                }
            }

            items(channels, key = { it.id }) { channel ->
                ClickableRow(onClick = { editing = channel }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(NotificationRegistry.byId(channel.provider)?.displayName ?: channel.provider)
                                    if (channel.isDefault) append(" · default")
                                    if (!channel.active) append(" · disabled")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val result = core.notificationDispatcher.test(channel)
                                feedbackIsError = result.isFailure
                                feedback = result.fold(
                                    onSuccess = { "Test notification sent through ${channel.name}" },
                                    onFailure = { "Test failed: ${it.message}" },
                                )
                            }
                        }) {
                            Text("Test")
                        }
                        TextButton(onClick = { pendingDelete = channel }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = NotificationChannel() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New channel") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun ChannelEditor(
    channel: NotificationChannel,
    onDismiss: () -> Unit,
    onSave: (NotificationChannel) -> Unit,
    onTest: (NotificationChannel) -> Unit,
) {
    var draft by remember(channel.id) { mutableStateOf(channel) }
    var error by remember { mutableStateOf("") }
    val provider: NotificationProvider? = NotificationRegistry.byId(draft.provider)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                if (channel.id == 0L) "New channel" else "Edit channel",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
        }

        item {
            Column {
                KomaDropdown(
                    label = "Provider",
                    options = NotificationRegistry.all,
                    selected = provider,
                    onSelect = { selected ->
                        draft = draft.copy(
                            provider = selected.id,
                            name = draft.name.ifBlank { selected.displayName },
                            config = selected.fields.associate { it.key to it.defaultValue }.filterValues { it.isNotEmpty() },
                        )
                    },
                    optionLabel = { it.displayName },
                )
                KomaField(draft.name, { draft = draft.copy(name = it) }, "Display name")
            }
        }

        item {
            Column {
                if (provider != null && provider.fields.isNotEmpty()) SectionTitle("Configuration")
                provider?.fields?.forEach { field ->
                    val value = draft.config[field.key] ?: field.defaultValue
                    when (field.type) {
                        ConfigFieldType.BOOLEAN -> SwitchRow(
                            label = field.label,
                            checked = value.toBooleanStrictOrNull() ?: false,
                            onCheckedChange = { checked ->
                                draft = draft.copy(config = draft.config + (field.key to checked.toString()))
                            },
                            helper = field.helpText,
                        )
                        ConfigFieldType.SELECT -> KomaDropdown(
                            label = field.label,
                            options = field.options,
                            selected = value.takeIf { it.isNotBlank() },
                            onSelect = { selected ->
                                draft = draft.copy(config = draft.config + (field.key to selected))
                            },
                            optionLabel = { it },
                        )
                        else -> KomaField(
                            value = value,
                            onValueChange = { input ->
                                draft = draft.copy(config = draft.config + (field.key to input))
                            },
                            label = field.label + if (field.required) " *" else "",
                            placeholder = field.placeholder,
                            helper = field.helpText,
                            password = field.type == ConfigFieldType.PASSWORD,
                            numeric = field.type == ConfigFieldType.NUMBER,
                            singleLine = field.type != ConfigFieldType.MULTILINE,
                            minLines = if (field.type == ConfigFieldType.MULTILINE) 3 else 1,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SwitchRow("Active", draft.active, { draft = draft.copy(active = it) })
                SwitchRow(
                    "Default for new monitors",
                    draft.isDefault,
                    { draft = draft.copy(isDefault = it) },
                    helper = "Pre-selected whenever you create a monitor",
                )
                InlineMessage(error, error = true)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val problems = buildList {
                                if (draft.name.isBlank()) add("The channel needs a name")
                                provider?.validate(draft.config)?.let { addAll(it) }
                            }
                            error = problems.joinToString("\n")
                            if (problems.isEmpty()) onSave(draft)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = { onTest(draft) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Test")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Back") }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
