package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Maintenance(
  public val id: Long,
  public val title: String,
  public val description: String?,
  public val strategy: String,
  public val active: Boolean,
  public val manual_active: Boolean,
  public val timezone: String?,
  public val start_ms: Long?,
  public val end_ms: Long?,
  public val start_time: String?,
  public val end_time: String?,
  public val weekdays: String?,
  public val days_of_month: String?,
  public val cron: String?,
  public val duration_minutes: Long?,
  public val interval_day: Long?,
  public val created_at: Long,
)
