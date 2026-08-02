package dev.haasele.koma.app.ui

import dev.haasele.koma.shared.core.toLocalDateTime
import dev.haasele.koma.shared.domain.MonitorStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.round

fun MonitorStatus.label(): String = when (this) {
    MonitorStatus.UP -> "Up"
    MonitorStatus.DOWN -> "Down"
    MonitorStatus.PENDING -> "Pending"
    MonitorStatus.MAINTENANCE -> "Maintenance"
}

fun Double.asPercent(decimals: Int = 2): String {
    val factor = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        else -> 100.0
    }
    val value = round(this * 100 * factor) / factor
    return "$value%"
}

fun Double?.asMillis(): String = this?.let { "${round(it).toLong()} ms" } ?: "—"

fun Long?.asMillis(): String = this?.let { "$it ms" } ?: "—"

/** `yyyy-MM-dd HH:mm`, the format the maintenance editor accepts back. */
fun Long.asInputTimestamp(): String {
    val dt = toLocalDateTime()
    return buildString {
        append(dt.year.toString().padStart(4, '0')).append('-')
        append((dt.month.ordinal + 1).toString().padStart(2, '0')).append('-')
        append(dt.day.toString().padStart(2, '0')).append(' ')
        append(dt.hour.toString().padStart(2, '0')).append(':')
        append(dt.minute.toString().padStart(2, '0'))
    }
}

fun String.parseInputTimestamp(zone: TimeZone = TimeZone.currentSystemDefault()): Long? {
    val parts = trim().split(' ', 'T').filter { it.isNotBlank() }
    if (parts.size != 2) return null
    val date = parts[0].split('-').mapNotNull { it.toIntOrNull() }
    val time = parts[1].split(':').mapNotNull { it.toIntOrNull() }
    if (date.size != 3 || time.size < 2) return null
    return runCatching {
        LocalDateTime(date[0], date[1], date[2], time[0], time[1]).toInstant(zone).toEpochMilliseconds()
    }.getOrNull()
}

fun weekdayName(iso: Int): String = when (iso) {
    1 -> "Mon"
    2 -> "Tue"
    3 -> "Wed"
    4 -> "Thu"
    5 -> "Fri"
    6 -> "Sat"
    else -> "Sun"
}
