package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Api_key(
  public val id: Long,
  public val name: String,
  public val key_hash: String,
  public val prefix: String,
  public val active: Boolean,
  public val expires_at: Long?,
  public val created_at: Long,
)
