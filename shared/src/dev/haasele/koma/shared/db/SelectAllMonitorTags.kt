package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class SelectAllMonitorTags(
  public val monitor_id: Long,
  public val id: Long,
  public val name: String,
  public val color: String,
  public val value_: String?,
)
