package dev.haasele.koma.shared.engine

import dev.haasele.koma.shared.domain.Maintenance
import dev.haasele.koma.shared.domain.MaintenanceStrategy
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun utcMs(text: String): Long = LocalDateTime.parse(text).toInstant(TimeZone.UTC).toEpochMilliseconds()

class MaintenanceEvaluatorTest {

    @Test
    fun `manual maintenance follows its toggle`() {
        val maintenance = Maintenance(strategy = MaintenanceStrategy.MANUAL, manualActive = true)
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T12:00:00")))
        assertFalse(
            MaintenanceEvaluator.isUnderMaintenance(
                maintenance.copy(manualActive = false),
                utcMs("2026-05-01T12:00:00"),
            ),
        )
    }

    @Test
    fun `single window covers only its range`() {
        val maintenance = Maintenance(
            strategy = MaintenanceStrategy.SINGLE,
            startMs = utcMs("2026-05-01T10:00:00"),
            endMs = utcMs("2026-05-01T12:00:00"),
        )
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T11:00:00")))
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T13:00:00")))
    }

    @Test
    fun `inactive maintenance never matches`() {
        val maintenance = Maintenance(
            strategy = MaintenanceStrategy.SINGLE,
            active = false,
            startMs = utcMs("2026-05-01T10:00:00"),
            endMs = utcMs("2026-05-01T12:00:00"),
        )
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T11:00:00")))
    }

    @Test
    fun `weekday window matches the configured day and time`() {
        // 2026-05-01 is a Friday, ISO day 5.
        val maintenance = Maintenance(
            strategy = MaintenanceStrategy.RECURRING_WEEKDAY,
            timezone = "UTC",
            weekdays = listOf(5),
            startTime = "22:00",
            endTime = "23:00",
        )
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T22:30:00")))
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T21:30:00")))
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-02T22:30:00")))
    }

    @Test
    fun `window crossing midnight stays open after zero hours`() {
        val maintenance = Maintenance(
            strategy = MaintenanceStrategy.RECURRING_WEEKDAY,
            timezone = "UTC",
            weekdays = listOf(1, 2, 3, 4, 5, 6, 7),
            startTime = "23:00",
            endTime = "02:00",
        )
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-01T23:30:00")))
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-02T01:30:00")))
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-02T03:30:00")))
    }

    @Test
    fun `day of month window matches`() {
        val maintenance = Maintenance(
            strategy = MaintenanceStrategy.RECURRING_DAY_OF_MONTH,
            timezone = "UTC",
            daysOfMonth = listOf(1, 15),
            startTime = "00:00",
            endTime = "23:59",
        )
        assertTrue(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-15T09:00:00")))
        assertFalse(MaintenanceEvaluator.isUnderMaintenance(maintenance, utcMs("2026-05-16T09:00:00")))
    }
}

class CronExpressionTest {

    @Test
    fun `wildcard matches every minute`() {
        assertTrue(CronExpression.matches("* * * * *", LocalDateTime.parse("2026-05-01T03:07:00")))
    }

    @Test
    fun `specific hour and minute match`() {
        assertTrue(CronExpression.matches("30 2 * * *", LocalDateTime.parse("2026-05-01T02:30:00")))
        assertFalse(CronExpression.matches("30 2 * * *", LocalDateTime.parse("2026-05-01T03:30:00")))
    }

    @Test
    fun `step values match`() {
        assertTrue(CronExpression.matches("*/15 * * * *", LocalDateTime.parse("2026-05-01T10:30:00")))
        assertFalse(CronExpression.matches("*/15 * * * *", LocalDateTime.parse("2026-05-01T10:31:00")))
    }

    @Test
    fun `ranges and lists match`() {
        assertTrue(CronExpression.matches("0 9-17 * * *", LocalDateTime.parse("2026-05-01T13:00:00")))
        assertFalse(CronExpression.matches("0 9-17 * * *", LocalDateTime.parse("2026-05-01T18:00:00")))
        assertTrue(CronExpression.matches("0 0 1,15 * *", LocalDateTime.parse("2026-05-15T00:00:00")))
    }

    @Test
    fun `validation rejects incomplete expressions`() {
        assertFalse(CronExpression.isValid("* * *"))
        assertTrue(CronExpression.isValid("*/5 * * * *"))
    }
}
