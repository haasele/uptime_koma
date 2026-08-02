package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Notification_channel(
  public val id: Long,
  public val name: String,
  public val provider: String,
  public val config: String,
  public val active: Boolean,
  public val is_default: Boolean,
  public val created_at: Long,
)
