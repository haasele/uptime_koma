package dev.haasele.koma.shared.db

import kotlin.Long
import kotlin.String

public data class Docker_host(
  public val id: Long,
  public val name: String,
  public val connection_type: String,
  public val daemon: String,
  public val created_at: Long,
)
