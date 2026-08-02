package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class Tag(
  public val id: Long,
  public val name: String,
  public val color: String,
)
