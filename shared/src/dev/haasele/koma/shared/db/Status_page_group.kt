package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class Status_page_group(
  public val id: Long,
  public val status_page_id: Long,
  public val name: String,
  public val weight: Long,
)
