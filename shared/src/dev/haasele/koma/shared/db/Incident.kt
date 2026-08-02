package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Incident(
  public val id: Long,
  public val status_page_id: Long,
  public val title: String,
  public val content: String,
  public val style: String,
  public val pin: Boolean,
  public val created_at: Long,
  public val last_updated_at: Long,
)
