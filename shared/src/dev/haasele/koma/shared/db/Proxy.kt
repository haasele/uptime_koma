package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Proxy(
  public val id: Long,
  public val protocol: String,
  public val host: String,
  public val port: Long,
  public val username: String?,
  public val password: String?,
  public val active: Boolean,
  public val is_default: Boolean,
  public val created_at: Long,
)
