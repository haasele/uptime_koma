package dev.haasele.koma.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import dev.haasele.koma.app.theme.LocalStatusPalette
import dev.haasele.koma.shared.data.DailyUptime
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.MonitorStatus

@Composable
fun StatusDot(status: MonitorStatus, active: Boolean = true, size: Int = 10) {
    val palette = LocalStatusPalette.current
    val target = if (active) palette.colorFor(status) else palette.paused
    val color by animateColorAsState(target)
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusBadge(status: MonitorStatus, active: Boolean = true) {
    val palette = LocalStatusPalette.current
    val color = if (active) palette.colorFor(status) else palette.paused
    val text = if (active) status.label() else "Paused"
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The beat strip from Uptime Koma: newest on the right, padded on the left so a young monitor
 * does not stretch three beats across the whole row.
 */
@Composable
fun HeartbeatBar(
    beats: List<Heartbeat>,
    modifier: Modifier = Modifier,
    slots: Int = 40,
    barHeight: Int = 28,
    active: Boolean = true,
) {
    val palette = LocalStatusPalette.current
    val visible = beats.takeLast(slots)
    val padding = (slots - visible.size).coerceAtLeast(0)
    val empty = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier.height(barHeight.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(padding) {
            Box(Modifier.weight(1f).height(barHeight.dp).clip(RoundedCornerShape(2.dp)).background(empty))
        }
        visible.forEach { beat ->
            val color = if (active) palette.colorFor(beat.status) else palette.paused
            Box(Modifier.weight(1f).height(barHeight.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

/** Response time trend for the detail screen. */
@Composable
fun PingSparkline(beats: List<Heartbeat>, modifier: Modifier = Modifier) {
    LatencySparkline(points = beats.mapNotNull { it.pingMs?.toFloat() }, modifier = modifier)
}

@Composable
fun LatencySparkline(points: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Not enough samples yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Canvas(modifier) {
        val max = points.max().coerceAtLeast(1f)
        val min = points.min()
        val span = (max - min).takeIf { it > 0f } ?: max
        val stepX = size.width / (points.size - 1)

        fun yFor(value: Float): Float {
            val normalized = (value - min) / span
            return size.height - (normalized * (size.height * 0.85f)) - size.height * 0.075f
        }

        val path = Path().apply {
            moveTo(0f, yFor(points.first()))
            points.forEachIndexed { index, value -> if (index > 0) lineTo(index * stepX, yFor(value)) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        drawCircle(line, radius = 3.dp.toPx(), center = Offset(size.width, yFor(points.last())))
    }
}

/** Horizontal mix of fleet statuses (up / down / pending / paused). */
@Composable
fun StatusMixBar(
    up: Int,
    down: Int,
    pending: Int,
    paused: Int,
    maintenance: Int = 0,
    modifier: Modifier = Modifier,
    barHeight: Int = 14,
) {
    val palette = LocalStatusPalette.current
    val segments = listOf(
        up to palette.up,
        down to palette.down,
        pending to palette.pending,
        maintenance to palette.maintenance,
        paused to palette.paused,
    ).filter { it.first > 0 }
    val total = segments.sumOf { it.first }.coerceAtLeast(1)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight.dp)
                .clip(RoundedCornerShape(barHeight.dp / 2)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.forEach { (count, color) ->
                Box(
                    Modifier
                        .weight(count.toFloat() / total)
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            data class Legend(val label: String, val count: Int, val color: Color)
            listOf(
                Legend("Up", up, palette.up),
                Legend("Down", down, palette.down),
                Legend("Pending", pending, palette.pending),
                Legend("Paused", paused, palette.paused),
            ).filter { it.count > 0 }.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${item.label} ${item.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Daily uptime as a bar per day; a missing day stays grey instead of shifting the timeline. */
@Composable
fun DailyUptimeChart(days: List<DailyUptime>, modifier: Modifier = Modifier) {
    val palette = LocalStatusPalette.current
    val empty = MaterialTheme.colorScheme.outlineVariant

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val color = when {
                !day.hasData -> empty
                day.down > 0 && day.ratio < 0.999 -> palette.down
                day.maintenance > 0 && day.up == 0L -> palette.maintenance
                else -> palette.up
            }
            val fraction = if (day.hasData) (0.25f + 0.75f * day.ratio.toFloat()).coerceIn(0.25f, 1f) else 0.25f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, accent: Color? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        trailing?.invoke()
    }
}

/** Rows are interactive, so they get a container; static content elsewhere stays flat. */
@Composable
fun ClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.clickable(onClick = onClick).padding(14.dp)) { content() }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun KomaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String = "",
    password: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    password -> KeyboardType.Password
                    numeric -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (helper.isNotBlank()) {
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String = "",
    min: Int = 0,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    KomaField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }
            onValueChange(text.toIntOrNull()?.coerceAtLeast(min) ?: min)
        },
        label = label,
        helper = helper,
        numeric = true,
        modifier = modifier,
    )
}

@Composable
fun <T> KomaDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selected?.let(optionLabel) ?: placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helper: String = "",
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (helper.isNotBlank()) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, secondary: String = "") {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (secondary.isNotBlank()) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InlineMessage(text: String, error: Boolean = false) {
    if (text.isBlank()) return
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(12.dp),
        )
    }
}

fun parseHexColor(value: String?, fallback: Color): Color {
    val hex = value?.trim()?.removePrefix("#") ?: return fallback
    if (hex.length != 6 && hex.length != 8) return fallback
    val parsed = hex.toLongOrNull(16) ?: return fallback
    return if (hex.length == 6) Color(parsed or 0xFF000000L) else Color(parsed)
}
