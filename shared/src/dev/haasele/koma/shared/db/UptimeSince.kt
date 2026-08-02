package dev.haasele.koma.shared.db

import kotlin.Double
import kotlin.Long

public data class UptimeSince(
  public val upCount: Long?,
  public val downCount: Long?,
  public val upSeconds: Long?,
  public val downSeconds: Long?,
  public val avgPing: Double?,
)
