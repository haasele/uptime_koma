package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Monitor(
  public val id: Long,
  public val name: String,
  public val type: String,
  public val active: Boolean,
  public val parent_id: Long?,
  public val description: String?,
  public val interval_seconds: Long,
  public val retry_interval_seconds: Long,
  public val resend_interval: Long,
  public val max_retries: Long,
  public val timeout_seconds: Long,
  public val upside_down: Boolean,
  public val push_token: String?,
  public val proxy_id: Long?,
  public val weight: Long,
  public val config: String,
  public val created_at: Long,
  public val updated_at: Long,
)
