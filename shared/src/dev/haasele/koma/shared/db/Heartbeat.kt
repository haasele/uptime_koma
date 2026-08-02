package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Heartbeat(
  public val id: Long,
  public val monitor_id: Long,
  public val status: Long,
  public val msg: String,
  public val ping_ms: Long?,
  public val important: Boolean,
  public val time_ms: Long,
  public val duration_seconds: Long,
  public val retries: Long,
  public val down_count: Long,
)
