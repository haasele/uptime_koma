package dev.haasele.koma.shared.db

import kotlin.Double
import kotlin.Long

public data class AggregateSince(
  public val upCount: Long?,
  public val downCount: Long?,
  public val pingSum: Double?,
  public val pingCount: Long?,
)
