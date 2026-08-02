package dev.haasele.koma.shared.db

import kotlin.Double
import kotlin.Long

public data class Stat_daily(
  public val monitor_id: Long,
  public val day_ms: Long,
  public val up: Long,
  public val down: Long,
  public val maintenance: Long,
  public val ping_sum: Double,
  public val ping_count: Long,
  public val ping_min: Double?,
  public val ping_max: Double?,
)
