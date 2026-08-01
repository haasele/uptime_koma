package dev.haasele.koma.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MaintenanceStrategy(val id: String, val label: String) {
    @SerialName("manual") MANUAL("manual", "Manual"),
    @SerialName("single") SINGLE("single", "Single window"),
    @SerialName("recurring-interval") RECURRING_INTERVAL("recurring-interval", "Recurring: every n days"),
    @SerialName("recurring-weekday") RECURRING_WEEKDAY("recurring-weekday", "Recurring: weekdays"),
    @SerialName("recurring-day-of-month") RECURRING_DAY_OF_MONTH("recurring-day-of-month", "Recurring: day of month"),
    @SerialName("cron") CRON("cron", "Cron expression");

    companion object {
        fun fromId(id: String): MaintenanceStrategy = entries.firstOrNull { it.id == id } ?: SINGLE
    }
}

@Serializable
enum class MaintenanceState { SCHEDULED, UNDER_MAINTENANCE, ENDED, INACTIVE, UNKNOWN }

@Serializable
data class Maintenance(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val strategy: MaintenanceStrategy = MaintenanceStrategy.SINGLE,
    val active: Boolean = true,
    val manualActive: Boolean = false,
    val timezone: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    /** Local time of day in `HH:mm` used by all recurring strategies. */
    val startTime: String? = null,
    val endTime: String? = null,
    /** ISO weekdays, 1 = Monday. */
    val weekdays: List<Int> = emptyList(),
    val daysOfMonth: List<Int> = emptyList(),
    val cron: String? = null,
    val durationMinutes: Int? = null,
    val intervalDay: Int? = null,
    val monitorIds: List<Long> = emptyList(),
    val statusPageIds: List<Long> = emptyList(),
    val createdAt: Long = 0,
)
