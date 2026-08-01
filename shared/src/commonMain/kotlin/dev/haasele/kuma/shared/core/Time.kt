package dev.haasele.koma.shared.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

fun Long.toLocalDateTime(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

fun Long.formatDateTime(zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = toLocalDateTime(zone)
    return buildString {
        append(dt.year.toString().padStart(4, '0')).append('-')
        append((dt.month.ordinal + 1).toString().padStart(2, '0')).append('-')
        append(dt.day.toString().padStart(2, '0')).append(' ')
        append(dt.hour.toString().padStart(2, '0')).append(':')
        append(dt.minute.toString().padStart(2, '0')).append(':')
        append(dt.second.toString().padStart(2, '0'))
    }
}

fun Long.formatTimeOnly(zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = toLocalDateTime(zone)
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:" +
        dt.second.toString().padStart(2, '0')
}

/** Compact relative label used in lists, e.g. `3m ago`. */
fun Long.relativeToNow(nowMs: Long = nowMs()): String {
    val delta = (nowMs - this) / 1000
    return when {
        delta < 0 -> "in the future"
        delta < 60 -> "${delta}s ago"
        delta < 3600 -> "${delta / 60}m ago"
        delta < 86_400 -> "${delta / 3600}h ago"
        else -> "${delta / 86_400}d ago"
    }
}

fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    seconds < 86_400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    else -> "${seconds / 86_400}d ${(seconds % 86_400) / 3600}h"
}
