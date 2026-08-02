package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.MaintenanceState
import dev.haasele.koma.shared.domain.MaintenanceStrategy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Decides whether a maintenance window covers a point in time. Recurring windows are evaluated
 * in the maintenance's own timezone so a nightly window does not drift with the viewer's locale.
 */
object MaintenanceEvaluator {

    fun isUnderMaintenance(maintenance: Maintenance, atMs: Long): Boolean {
        if (!maintenance.active) return false
        return when (maintenance.strategy) {
            MaintenanceStrategy.MANUAL -> maintenance.manualActive
            MaintenanceStrategy.SINGLE -> {
                val start = maintenance.startMs ?: return false
                val end = maintenance.endMs ?: return false
                atMs in start..end
            }
            MaintenanceStrategy.RECURRING_INTERVAL -> matchesInterval(maintenance, atMs)
            MaintenanceStrategy.RECURRING_WEEKDAY -> matchesWeekday(maintenance, atMs)
            MaintenanceStrategy.RECURRING_DAY_OF_MONTH -> matchesDayOfMonth(maintenance, atMs)
            MaintenanceStrategy.CRON -> matchesCron(maintenance, atMs)
        }
    }

    fun state(maintenance: Maintenance, atMs: Long): MaintenanceState = when {
        !maintenance.active -> MaintenanceState.INACTIVE
        isUnderMaintenance(maintenance, atMs) -> MaintenanceState.UNDER_MAINTENANCE
        maintenance.strategy == MaintenanceStrategy.SINGLE && (maintenance.endMs ?: 0) < atMs ->
            MaintenanceState.ENDED
        else -> MaintenanceState.SCHEDULED
    }

    private fun zoneOf(maintenance: Maintenance): TimeZone =
        maintenance.timezone
            ?.takeIf { it.isNotBlank() && it != "SAME_AS_SERVER" }
            ?.let { runCatching { TimeZone.of(it) }.getOrNull() }
            ?: TimeZone.currentSystemDefault()

    private fun withinDailyWindow(maintenance: Maintenance, local: LocalDateTime): Boolean {
        val start = parseTime(maintenance.startTime) ?: return false
        val end = parseTime(maintenance.endTime) ?: return false
        val minuteOfDay = local.hour * 60 + local.minute
        val startMinute = start.hour * 60 + start.minute
        val endMinute = end.hour * 60 + end.minute
        return if (startMinute <= endMinute) {
            minuteOfDay in startMinute..endMinute
        } else {
            // Window crosses midnight, e.g. 23:00 - 02:00.
            minuteOfDay >= startMinute || minuteOfDay <= endMinute
        }
    }

    private fun matchesWeekday(maintenance: Maintenance, atMs: Long): Boolean {
        if (maintenance.weekdays.isEmpty()) return false
        val zone = zoneOf(maintenance)
        val local = Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone)
        val isoDay = local.dayOfWeek.isoDayNumber
        if (isoDay !in maintenance.weekdays) return false
        return withinDailyWindow(maintenance, local) && withinRange(maintenance, atMs)
    }

    private fun matchesDayOfMonth(maintenance: Maintenance, atMs: Long): Boolean {
        if (maintenance.daysOfMonth.isEmpty()) return false
        val zone = zoneOf(maintenance)
        val local = Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone)
        if (local.day !in maintenance.daysOfMonth) return false
        return withinDailyWindow(maintenance, local) && withinRange(maintenance, atMs)
    }

    private fun matchesInterval(maintenance: Maintenance, atMs: Long): Boolean {
        val everyDays = maintenance.intervalDay ?: return false
        if (everyDays <= 0) return false
        val startMs = maintenance.startMs ?: return false
        val zone = zoneOf(maintenance)
        val startDay = Instant.fromEpochMilliseconds(startMs).toLocalDateTime(zone).date
        val currentLocal = Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone)
        val daysBetween = daysBetween(startDay, currentLocal.date)
        if (daysBetween < 0 || daysBetween % everyDays != 0L) return false
        return withinDailyWindow(maintenance, currentLocal) && withinRange(maintenance, atMs)
    }

    private fun matchesCron(maintenance: Maintenance, atMs: Long): Boolean {
        val expression = maintenance.cron ?: return false
        val duration = maintenance.durationMinutes ?: return false
        val zone = zoneOf(maintenance)
        val local = Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone)
        if (!withinRange(maintenance, atMs)) return false

        // A window is open when some minute within the trailing duration matched the expression.
        for (minutesAgo in 0 until duration) {
            val candidate = Instant.fromEpochMilliseconds(atMs - minutesAgo * 60_000L).toLocalDateTime(zone)
            if (CronExpression.matches(expression, candidate)) return true
        }
        return local.let { false }
    }

    private fun withinRange(maintenance: Maintenance, atMs: Long): Boolean {
        val start = maintenance.startMs
        val end = maintenance.endMs
        if (start != null && atMs < start) return false
        if (end != null && atMs > end) return false
        return true
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return runCatching { LocalTime(hour, minute) }.getOrNull()
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Long {
        val zone = TimeZone.UTC
        val fromMs = from.atStartOfDayIn(zone).toEpochMilliseconds()
        val toMs = to.atStartOfDayIn(zone).toEpochMilliseconds()
        return (toMs - fromMs) / 86_400_000L
    }
}

private val kotlinx.datetime.DayOfWeek.isoDayNumber: Int get() = ordinal + 1

/** Supports the standard five field cron syntax with `*`, lists, ranges and steps. */
object CronExpression {

    fun matches(expression: String, time: LocalDateTime): Boolean {
        val fields = expression.trim().split(Regex("\\s+"))
        if (fields.size < 5) return false
        return matchesField(fields[0], time.minute) &&
            matchesField(fields[1], time.hour) &&
            matchesField(fields[2], time.day) &&
            matchesField(fields[3], time.month.ordinal + 1) &&
            matchesField(fields[4], time.dayOfWeek.ordinal + 1, allowSundayZero = true)
    }

    fun isValid(expression: String): Boolean {
        val fields = expression.trim().split(Regex("\\s+"))
        if (fields.size < 5) return false
        return fields.take(5).all { field ->
            field.split(",").all { part ->
                val withoutStep = part.substringBefore("/")
                val step = part.substringAfter("/", "1").toIntOrNull()
                step != null && (withoutStep == "*" || withoutStep.split("-").all { it.toIntOrNull() != null })
            }
        }
    }

    private fun matchesField(field: String, value: Int, allowSundayZero: Boolean = false): Boolean =
        field.split(",").any { part -> matchesPart(part, value, allowSundayZero) }

    private fun matchesPart(part: String, value: Int, allowSundayZero: Boolean): Boolean {
        val step = part.substringAfter("/", "1").toIntOrNull() ?: return false
        val range = part.substringBefore("/")
        val normalized = if (allowSundayZero && value == 7) listOf(7, 0) else listOf(value)

        return normalized.any { candidate ->
            when {
                range == "*" -> step == 1 || candidate % step == 0
                range.contains("-") -> {
                    val bounds = range.split("-", limit = 2)
                    val start = bounds[0].toIntOrNull() ?: return@any false
                    val end = bounds[1].toIntOrNull() ?: return@any false
                    candidate in start..end && (candidate - start) % step == 0
                }
                else -> range.toIntOrNull() == candidate
            }
        }
    }
}
