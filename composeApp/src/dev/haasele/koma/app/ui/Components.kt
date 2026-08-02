package dev.haasele.koma.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.haasele.koma.app.nav.LocalNavSettled
import dev.haasele.koma.app.theme.KomaMotion
import dev.haasele.koma.app.theme.LocalStatusPalette
import dev.haasele.koma.shared.data.DailyUptime
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.MonitorStatus

@Composable
fun StatusDot(status: MonitorStatus, active: Boolean = true, size: Int = 10) {
    val palette = LocalStatusPalette.current
    val target = if (active) palette.colorFor(status) else palette.paused
    val color by animateColorAsState(target, animationSpec = KomaMotion.status(), label = "status-dot")
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun StatusBadge(status: MonitorStatus, active: Boolean = true) {
    val palette = LocalStatusPalette.current
    val target = if (active) palette.colorFor(status) else palette.paused
    val color by animateColorAsState(target, animationSpec = KomaMotion.status(), label = "status-badge")
    val text = if (active) status.label() else "Paused"
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { KomaMotion.contentCrossfade() },
            label = "status-badge-label",
        ) { label ->
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Heartbeat strip adapted from Uptime Kuma's HeartbeatBar:
 * vertical capsules sized near Kuma's 5/10dp, packed so the strip fills the full width;
 * when a new beat arrives at capacity the strip slides left before settling.
 *
 * Under capacity the existing beats stretch across the full width (no gray empty pads).
 * Only the newly appended rightmost capsule plays enter motion.
 *
 * @param maxSlots upper bound on how many capsules to render (data headroom)
 * @param onVisibleSlotsChange reports how many capsules currently fit (for dynamic titles)
 */
@Composable
fun HeartbeatBar(
    beats: List<Heartbeat>,
    modifier: Modifier = Modifier,
    maxSlots: Int = 200,
    barHeight: Int = 28,
    active: Boolean = true,
    onVisibleSlotsChange: (Int) -> Unit = {},
) {
    val palette = LocalStatusPalette.current
    val empty = MaterialTheme.colorScheme.outlineVariant
    // Kuma target sizes: big → 10×30 pad 4; small → 5×16 pad 2
    val preferredWidth = if (barHeight <= 22) 5.dp else 10.dp
    val gap = if (barHeight <= 22) 2.dp else 4.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(barHeight.dp)
            .clip(RectangleShape),
    ) {
        // Max capsules that fit at the preferred Kuma size.
        val capacity = minOf(
            maxSlots,
            maxOf(1, ((maxWidth + gap) / (preferredWidth + gap)).toInt()),
        )
        LaunchedEffect(capacity) { onVisibleSlotsChange(capacity) }

        val visible = remember(beats, capacity) { beats.takeLast(capacity) }
        fun beatKey(beat: Heartbeat): Long = if (beat.id != 0L) beat.id else beat.timeMs
        val visibleKeys = remember(visible) { visible.map(::beatKey) }

        // Under-filled: stretch beats across the full width (no leading empty pads).
        // At capacity: fixed Kuma slot width + left-slide on new beats.
        val atCapacity = visible.size >= capacity
        val layoutSlots = when {
            visible.isEmpty() -> capacity
            atCapacity -> capacity
            else -> visible.size
        }
        val beatWidth = if (layoutSlots <= 1) {
            maxWidth
        } else {
            (maxWidth - gap * (layoutSlots - 1)) / layoutSlots
        }
        val cellPx = with(density) { (beatWidth + gap).toPx() }
        val capsule = RoundedCornerShape(beatWidth / 2)

        var priorKeys by remember { mutableStateOf<List<Long>>(emptyList()) }
        var priorBeats by remember { mutableStateOf<List<Heartbeat>>(emptyList()) }
        var slideBeats by remember { mutableStateOf<List<Heartbeat>?>(null) }
        var enteringKey by remember { mutableStateOf<Long?>(null) }
        val slideOffset = remember { Animatable(0f) }

        LaunchedEffect(visibleKeys, capacity) {
            val nextKey = visibleKeys.lastOrNull()
            try {
                val previous = priorBeats
                val previousKeys = priorKeys
                val newBeatArrived =
                    previousKeys.isNotEmpty() && nextKey != null && nextKey != previousKeys.lastOrNull()

                if (newBeatArrived) {
                    enteringKey = nextKey
                    // One-slot slide only once the strip is at capacity (Kuma push-left).
                    if (previous.size >= capacity) {
                        val outgoing = previous.firstOrNull()?.takeIf { beat ->
                            visibleKeys.none { it == beatKey(beat) }
                        }
                        if (outgoing != null) {
                            slideBeats = listOf(outgoing) + visible.takeLast(capacity)
                            slideOffset.snapTo(0f)
                            slideOffset.animateTo(-1f, animationSpec = KomaMotion.beatSlide())
                        }
                    }
                } else {
                    enteringKey = null
                }
            } finally {
                slideBeats = null
                slideOffset.snapTo(0f)
                priorBeats = visible
                priorKeys = visibleKeys
            }
        }

        val displayBeats = slideBeats ?: visible
        // Leading empty pads only when we have zero beats (reserve the track).
        val displayEmpty = if (visible.isEmpty()) capacity else 0

        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = slideOffset.value * cellPx }
                .height(barHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(displayEmpty) { index ->
                key("pad-$index") {
                    Box(
                        Modifier
                            .width(beatWidth)
                            .height(barHeight.dp)
                            .clip(capsule)
                            .background(empty),
                    )
                }
            }
            displayBeats.forEach { beat ->
                key(beatKey(beat)) {
                    BeatCapsule(
                        targetColor = if (active) palette.colorFor(beat.status) else palette.paused,
                        emptyColor = empty,
                        beatWidth = beatWidth,
                        barHeight = barHeight,
                        shape = capsule,
                        playEntrance = beatKey(beat) == enteringKey,
                    )
                }
            }
        }
    }
}

/** Single heartbeat pill: expands in and colour-tweens from empty → status. */
@Composable
private fun BeatCapsule(
    targetColor: Color,
    emptyColor: Color,
    beatWidth: Dp,
    barHeight: Int,
    shape: RoundedCornerShape,
    playEntrance: Boolean,
) {
    if (!playEntrance) {
        Box(
            Modifier
                .width(beatWidth)
                .height(barHeight.dp)
                .clip(shape)
                .background(targetColor),
        )
        return
    }

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val color by animateColorAsState(
        targetValue = if (settled) targetColor else emptyColor,
        animationSpec = KomaMotion.status(),
        label = "beat-color",
    )
    val scale by animateFloatAsState(
        targetValue = if (settled) 1f else 0.2f,
        animationSpec = KomaMotion.beatSlide(),
        label = "beat-expand",
    )

    Box(
        Modifier
            .width(beatWidth)
            .height(barHeight.dp)
            .graphicsLayer { scaleY = scale }
            .clip(shape)
            .background(color),
    )
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

    // Morph between series like Chart.js — resample on a shared X grid and lerp Y.
    val navSettled = LocalNavSettled.current
    var fromSeries by remember { mutableStateOf(points) }
    var toSeries by remember { mutableStateOf(points) }
    val morph = remember { Animatable(1f) }
    var bootstrapped by remember { mutableStateOf(false) }

    LaunchedEffect(points, navSettled) {
        if (!navSettled) {
            fromSeries = points
            toSeries = points
            morph.snapTo(1f)
            bootstrapped = true
            return@LaunchedEffect
        }
        if (!bootstrapped) {
            fromSeries = points
            toSeries = points
            morph.snapTo(1f)
            bootstrapped = true
            return@LaunchedEffect
        }
        fromSeries = morphSeries(fromSeries, toSeries, morph.value)
        toSeries = points
        if (fromSeries == toSeries) {
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        morph.snapTo(0f)
        morph.animateTo(1f, animationSpec = KomaMotion.chart())
    }

    val drawPoints = morphSeries(fromSeries, toSeries, morph.value)
    // Scale follows the morphing series directly — a second min/max tween fought the path morph.
    val scaleMin = drawPoints.min()
    val scaleMax = drawPoints.max().coerceAtLeast(1f)

    Canvas(modifier) {
        if (drawPoints.size < 2) return@Canvas
        val span = (scaleMax - scaleMin).takeIf { it > 0f } ?: scaleMax

        fun yFor(value: Float): Float {
            val normalized = (value - scaleMin) / span
            return size.height - (normalized * (size.height * 0.85f)) - size.height * 0.075f
        }

        val coords = drawPoints.mapIndexed { index, value ->
            val x = size.width * index / (drawPoints.size - 1).toFloat()
            Offset(x, yFor(value))
        }

        val path = Path().apply { smoothLineThrough(coords, tension = 0.2f) }
        val area = Path().apply {
            addPath(path)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }
        drawPath(area, fill)
        // Kuma PingChart: tension 0.2, point radius 0 — smooth line only.
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Lerp two series onto a shared sample count so the path morphs instead of jumping. */
private fun morphSeries(from: List<Float>, to: List<Float>, t: Float): List<Float> {
    if (from.isEmpty()) return to
    if (to.isEmpty()) return from
    if (t <= 0f) return from
    if (t >= 1f) return to
    val samples = maxOf(from.size, to.size, 2)
    return List(samples) { i ->
        val x = i / (samples - 1).toFloat()
        val y0 = sampleSeries(from, x)
        val y1 = sampleSeries(to, x)
        y0 + (y1 - y0) * t
    }
}

private fun sampleSeries(series: List<Float>, x: Float): Float {
    if (series.isEmpty()) return 0f
    if (series.size == 1) return series[0]
    val pos = (x.coerceIn(0f, 1f)) * (series.size - 1)
    val i = pos.toInt().coerceIn(0, series.size - 2)
    val f = pos - i
    return series[i] + (series[i + 1] - series[i]) * f
}

/** Chart.js-like cardinal spline (tension ≈ 0.2). */
private fun Path.smoothLineThrough(points: List<Offset>, tension: Float = 0.2f) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    if (points.size == 1) return
    if (points.size == 2) {
        lineTo(points[1].x, points[1].y)
        return
    }
    for (i in 0 until points.lastIndex) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val d1x = (p2.x - p0.x) * tension
        val d1y = (p2.y - p0.y) * tension
        val d2x = (p3.x - p1.x) * tension
        val d2y = (p3.y - p1.y) * tension
        cubicTo(
            p1.x + d1x / 3f,
            p1.y + d1y / 3f,
            p2.x - d2x / 3f,
            p2.y - d2y / 3f,
            p2.x,
            p2.y,
        )
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
    val upW by animateFloatAsState(up.toFloat(), animationSpec = KomaMotion.status(), label = "mix-up")
    val downW by animateFloatAsState(down.toFloat(), animationSpec = KomaMotion.status(), label = "mix-down")
    val pendingW by animateFloatAsState(pending.toFloat(), animationSpec = KomaMotion.status(), label = "mix-pending")
    val maintenanceW by animateFloatAsState(maintenance.toFloat(), animationSpec = KomaMotion.status(), label = "mix-maint")
    val pausedW by animateFloatAsState(paused.toFloat(), animationSpec = KomaMotion.status(), label = "mix-paused")

    val segments = listOf(
        upW to palette.up,
        downW to palette.down,
        pendingW to palette.pending,
        maintenanceW to palette.maintenance,
        pausedW to palette.paused,
    ).filter { it.first > 0.01f }
    val total = segments.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.01f)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight.dp)
                .clip(RoundedCornerShape(barHeight.dp / 2)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.forEach { (weight, color) ->
                Box(
                    Modifier
                        .weight(weight / total)
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
                    val color by animateColorAsState(item.color, animationSpec = KomaMotion.status(), label = "legend-${item.label}")
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = "${item.label} ${item.count}",
                        transitionSpec = { KomaMotion.contentCrossfade() },
                        label = "mix-legend",
                    ) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Daily uptime capsules — near-fixed width like HeartbeatBar, packed to fill the row width.
 * Renders as many day pills as fit; pad left when history is short.
 */
@Composable
fun DailyUptimeChart(
    days: List<DailyUptime>,
    modifier: Modifier = Modifier,
    barWidth: Int = 8,
    gap: Int = 3,
    onVisibleDaysChange: (Int) -> Unit = {},
) {
    val palette = LocalStatusPalette.current
    val empty = MaterialTheme.colorScheme.outlineVariant
    val preferred = barWidth.dp
    val spacing = gap.dp

    BoxWithConstraints(modifier.fillMaxWidth().clip(RectangleShape)) {
        val capacity = maxOf(1, ((maxWidth + spacing) / (preferred + spacing)).toInt())
        val width = if (capacity <= 1) {
            maxWidth
        } else {
            (maxWidth - spacing * (capacity - 1)) / capacity
        }
        val capsule = RoundedCornerShape(width / 2)
        LaunchedEffect(capacity) { onVisibleDaysChange(capacity) }

        val visible = days.takeLast(capacity)
        val emptySlots = (capacity - visible.size).coerceAtLeast(0)

        Row(
            Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(emptySlots) { index ->
                key("day-pad-$index") {
                    Box(
                        Modifier
                            .width(width)
                            .fillMaxHeight(0.25f)
                            .clip(capsule)
                            .background(empty),
                    )
                }
            }
            visible.forEachIndexed { index, day ->
                key(day.dayMs) {
                    val targetColor = when {
                        !day.hasData -> empty
                        day.down > 0 && day.ratio < 0.999 -> palette.down
                        day.maintenance > 0 && day.up == 0L -> palette.maintenance
                        else -> palette.up
                    }
                    val targetFraction = if (day.hasData) {
                        (0.25f + 0.75f * day.ratio.toFloat()).coerceIn(0.25f, 1f)
                    } else {
                        0.25f
                    }
                    var settled by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { settled = true }
                    val color by animateColorAsState(
                        targetValue = if (settled) targetColor else empty,
                        animationSpec = KomaMotion.status(),
                        label = "day-color-$index",
                    )
                    val fraction by animateFloatAsState(
                        targetValue = if (settled) targetFraction else 0.15f,
                        animationSpec = KomaMotion.chart(),
                        label = "day-h-$index",
                    )
                    Box(
                        Modifier
                            .width(width)
                            .fillMaxHeight(fraction)
                            .clip(capsule)
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, accent: Color? = null, modifier: Modifier = Modifier) {
    val accentColor by animateColorAsState(
        targetValue = accent ?: MaterialTheme.colorScheme.onSurface,
        animationSpec = KomaMotion.status(),
        label = "metric-accent",
    )
    Column(modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(KomaMotion.page()) + slideInVertically(KomaMotion.page()) { it / 3 }) togetherWith
                    (fadeOut(KomaMotion.quick()) + slideOutVertically(KomaMotion.page()) { -it / 3 })
            },
            label = "metric-value",
        ) { display ->
            Text(
                text = display,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
            )
        }
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
        color = MaterialTheme.colorScheme.surfaceContainer,
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
) {
    var text by remember { mutableStateOf(value.toString()) }

    // Sync from parent only when not mid-edit (empty) and local parse differs.
    LaunchedEffect(value) {
        if (text.isEmpty()) return@LaunchedEffect
        val parsed = text.toIntOrNull()
        if (parsed != value) {
            text = value.toString()
        }
    }

    KomaField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }
            text = filtered
            filtered.toIntOrNull()?.let(onValueChange)
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
