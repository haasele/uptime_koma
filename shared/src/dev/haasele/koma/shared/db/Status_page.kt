package dev.haasele.koma.shared.db

import kotlin.Boolean
import kotlin.Long
import kotlin.String

public data class Status_page(
  public val id: Long,
  public val slug: String,
  public val title: String,
  public val description: String?,
  public val icon: String?,
  public val theme: String,
  public val published: Boolean,
  public val show_tags: Boolean,
  public val show_uptime_percentage: Boolean,
  public val show_certificate_expiry: Boolean,
  public val footer_text: String?,
  public val accent_color: String?,
  public val password_hash: String?,
  public val created_at: Long,
)
