package dev.haasele.koma.shared.db

import kotlin.Long

public data class Status_page_group_monitor(
  public val group_id: Long,
  public val monitor_id: Long,
  public val weight: Long,
)
