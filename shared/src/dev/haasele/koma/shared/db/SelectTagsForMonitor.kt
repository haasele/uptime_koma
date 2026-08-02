package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class SelectTagsForMonitor(
  public val id: Long,
  public val name: String,
  public val color: String,
  public val value_: String?,
)
