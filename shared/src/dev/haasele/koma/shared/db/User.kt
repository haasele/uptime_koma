package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class User(
  public val id: Long,
  public val username: String,
  public val password_hash: String,
  public val twofa_secret: String?,
  public val twofa_enabled: Boolean,
  public val created_at: Long,
)
