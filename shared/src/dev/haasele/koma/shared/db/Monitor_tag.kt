package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class Monitor_tag(
  public val monitor_id: Long,
  public val tag_id: Long,
  public val value_: String?,
)
