package dev.haasele.koma.shared.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Boolean
import kotlin.Long
import kotlin.String

public class StatusPageQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllPages(mapper: (
    id: Long,
    slug: String,
    title: String,
    description: String?,
    icon: String?,
    theme: String,
    published: Boolean,
    show_tags: Boolean,
    show_uptime_percentage: Boolean,
    show_certificate_expiry: Boolean,
    footer_text: String?,
    accent_color: String?,
    password_hash: String?,
    created_at: Long,
  ) -> T): Query<T> = Query(-1_172_828_381, arrayOf("status_page"), driver, "StatusPage.sq", "selectAllPages", "SELECT status_page.id, status_page.slug, status_page.title, status_page.description, status_page.icon, status_page.theme, status_page.published, status_page.show_tags, status_page.show_uptime_percentage, status_page.show_certificate_expiry, status_page.footer_text, status_page.accent_color, status_page.password_hash, status_page.created_at FROM status_page ORDER BY title ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getBoolean(6)!!,
      cursor.getBoolean(7)!!,
      cursor.getBoolean(8)!!,
      cursor.getBoolean(9)!!,
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getLong(13)!!
    )
  }

  public fun selectAllPages(): Query<Status_page> = selectAllPages(::Status_page)

  public fun <T : Any> selectPageById(id: Long, mapper: (
    id: Long,
    slug: String,
    title: String,
    description: String?,
    icon: String?,
    theme: String,
    published: Boolean,
    show_tags: Boolean,
    show_uptime_percentage: Boolean,
    show_certificate_expiry: Boolean,
    footer_text: String?,
    accent_color: String?,
    password_hash: String?,
    created_at: Long,
  ) -> T): Query<T> = SelectPageByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getBoolean(6)!!,
      cursor.getBoolean(7)!!,
      cursor.getBoolean(8)!!,
      cursor.getBoolean(9)!!,
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getLong(13)!!
    )
  }

  public fun selectPageById(id: Long): Query<Status_page> = selectPageById(id, ::Status_page)

  public fun <T : Any> selectPageBySlug(slug: String, mapper: (
    id: Long,
    slug: String,
    title: String,
    description: String?,
    icon: String?,
    theme: String,
    published: Boolean,
    show_tags: Boolean,
    show_uptime_percentage: Boolean,
    show_certificate_expiry: Boolean,
    footer_text: String?,
    accent_color: String?,
    password_hash: String?,
    created_at: Long,
  ) -> T): Query<T> = SelectPageBySlugQuery(slug) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getBoolean(6)!!,
      cursor.getBoolean(7)!!,
      cursor.getBoolean(8)!!,
      cursor.getBoolean(9)!!,
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getLong(13)!!
    )
  }

  public fun selectPageBySlug(slug: String): Query<Status_page> = selectPageBySlug(slug, ::Status_page)

  public fun <T : Any> selectGroups(status_page_id: Long, mapper: (
    id: Long,
    status_page_id: Long,
    name: String,
    weight: Long,
  ) -> T): Query<T> = SelectGroupsQuery(status_page_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!
    )
  }

  public fun selectGroups(status_page_id: Long): Query<Status_page_group> = selectGroups(status_page_id, ::Status_page_group)

  public fun <T : Any> selectGroupMonitors(group_id: Long, mapper: (
    group_id: Long,
    monitor_id: Long,
    weight: Long,
  ) -> T): Query<T> = SelectGroupMonitorsQuery(group_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!
    )
  }

  public fun selectGroupMonitors(group_id: Long): Query<Status_page_group_monitor> = selectGroupMonitors(group_id, ::Status_page_group_monitor)

  public fun <T : Any> selectIncidents(status_page_id: Long, mapper: (
    id: Long,
    status_page_id: Long,
    title: String,
    content: String,
    style: String,
    pin: Boolean,
    created_at: Long,
    last_updated_at: Long,
  ) -> T): Query<T> = SelectIncidentsQuery(status_page_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun selectIncidents(status_page_id: Long): Query<Incident> = selectIncidents(status_page_id, ::Incident)

  public fun <T : Any> selectPinnedIncident(status_page_id: Long, mapper: (
    id: Long,
    status_page_id: Long,
    title: String,
    content: String,
    style: String,
    pin: Boolean,
    created_at: Long,
    last_updated_at: Long,
  ) -> T): Query<T> = SelectPinnedIncidentQuery(status_page_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun selectPinnedIncident(status_page_id: Long): Query<Incident> = selectPinnedIncident(status_page_id, ::Incident)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(1_561_764_589, driver, "StatusPage.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertPage(
    slug: String,
    title: String,
    description: String?,
    icon: String?,
    theme: String,
    published: Boolean,
    show_tags: Boolean,
    show_uptime_percentage: Boolean,
    show_certificate_expiry: Boolean,
    footer_text: String?,
    accent_color: String?,
    password_hash: String?,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_634_879_924, """
        |INSERT INTO status_page (slug, title, description, icon, theme, published, show_tags,
        |    show_uptime_percentage, show_certificate_expiry, footer_text, accent_color, password_hash, created_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 13) {
          var parameterIndex = 0
          bindString(parameterIndex++, slug)
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, description)
          bindString(parameterIndex++, icon)
          bindString(parameterIndex++, theme)
          bindBoolean(parameterIndex++, published)
          bindBoolean(parameterIndex++, show_tags)
          bindBoolean(parameterIndex++, show_uptime_percentage)
          bindBoolean(parameterIndex++, show_certificate_expiry)
          bindString(parameterIndex++, footer_text)
          bindString(parameterIndex++, accent_color)
          bindString(parameterIndex++, password_hash)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(-1_634_879_924) { emit ->
      emit("status_page")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePage(
    slug: String,
    title: String,
    description: String?,
    icon: String?,
    theme: String,
    published: Boolean,
    show_tags: Boolean,
    show_uptime_percentage: Boolean,
    show_certificate_expiry: Boolean,
    footer_text: String?,
    accent_color: String?,
    password_hash: String?,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_397_990_492, """
        |UPDATE status_page SET slug = ?, title = ?, description = ?, icon = ?, theme = ?, published = ?,
        |    show_tags = ?, show_uptime_percentage = ?, show_certificate_expiry = ?, footer_text = ?,
        |    accent_color = ?, password_hash = ? WHERE id = ?
        """.trimMargin(), 13) {
          var parameterIndex = 0
          bindString(parameterIndex++, slug)
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, description)
          bindString(parameterIndex++, icon)
          bindString(parameterIndex++, theme)
          bindBoolean(parameterIndex++, published)
          bindBoolean(parameterIndex++, show_tags)
          bindBoolean(parameterIndex++, show_uptime_percentage)
          bindBoolean(parameterIndex++, show_certificate_expiry)
          bindString(parameterIndex++, footer_text)
          bindString(parameterIndex++, accent_color)
          bindString(parameterIndex++, password_hash)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_397_990_492) { emit ->
      emit("status_page")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deletePage(id: Long): QueryResult<Long> {
    val result = driver.execute(-836_456_386, """DELETE FROM status_page WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-836_456_386) { emit ->
      emit("status_page")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertGroup(
    status_page_id: Long,
    name: String,
    weight: Long,
  ): QueryResult<Long> {
    val result = driver.execute(850_532_962, """INSERT INTO status_page_group (status_page_id, name, weight) VALUES (?, ?, ?)""", 3) {
          var parameterIndex = 0
          bindLong(parameterIndex++, status_page_id)
          bindString(parameterIndex++, name)
          bindLong(parameterIndex++, weight)
        }
    notifyQueries(850_532_962) { emit ->
      emit("status_page_group")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteGroupsForPage(status_page_id: Long): QueryResult<Long> {
    val result = driver.execute(1_354_384_821, """DELETE FROM status_page_group WHERE status_page_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, status_page_id)
        }
    notifyQueries(1_354_384_821) { emit ->
      emit("status_page_group")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertGroupMonitor(
    group_id: Long,
    monitor_id: Long,
    weight: Long,
  ): QueryResult<Long> {
    val result = driver.execute(280_570_008, """INSERT OR REPLACE INTO status_page_group_monitor (group_id, monitor_id, weight) VALUES (?, ?, ?)""", 3) {
          var parameterIndex = 0
          bindLong(parameterIndex++, group_id)
          bindLong(parameterIndex++, monitor_id)
          bindLong(parameterIndex++, weight)
        }
    notifyQueries(280_570_008) { emit ->
      emit("status_page_group_monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteGroupMonitors(group_id: Long): QueryResult<Long> {
    val result = driver.execute(1_544_159_529, """DELETE FROM status_page_group_monitor WHERE group_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, group_id)
        }
    notifyQueries(1_544_159_529) { emit ->
      emit("status_page_group_monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertIncident(
    status_page_id: Long,
    title: String,
    content: String,
    style: String,
    pin: Boolean,
    created_at: Long,
    last_updated_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_796_108_815, """
        |INSERT INTO incident (status_page_id, title, content, style, pin, created_at, last_updated_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          var parameterIndex = 0
          bindLong(parameterIndex++, status_page_id)
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, content)
          bindString(parameterIndex++, style)
          bindBoolean(parameterIndex++, pin)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, last_updated_at)
        }
    notifyQueries(1_796_108_815) { emit ->
      emit("incident")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateIncident(
    title: String,
    content: String,
    style: String,
    pin: Boolean,
    last_updated_at: Long,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_343_150_111, """UPDATE incident SET title = ?, content = ?, style = ?, pin = ?, last_updated_at = ? WHERE id = ?""", 6) {
          var parameterIndex = 0
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, content)
          bindString(parameterIndex++, style)
          bindBoolean(parameterIndex++, pin)
          bindLong(parameterIndex++, last_updated_at)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_343_150_111) { emit ->
      emit("incident")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unpinIncidents(status_page_id: Long): QueryResult<Long> {
    val result = driver.execute(1_290_501_193, """UPDATE incident SET pin = 0 WHERE status_page_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, status_page_id)
        }
    notifyQueries(1_290_501_193) { emit ->
      emit("incident")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteIncident(id: Long): QueryResult<Long> {
    val result = driver.execute(-1_579_998_463, """DELETE FROM incident WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_579_998_463) { emit ->
      emit("incident")
    }
    return result
  }

  private inner class SelectPageByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("status_page", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("status_page", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-2_097_741_951, """SELECT status_page.id, status_page.slug, status_page.title, status_page.description, status_page.icon, status_page.theme, status_page.published, status_page.show_tags, status_page.show_uptime_percentage, status_page.show_certificate_expiry, status_page.footer_text, status_page.accent_color, status_page.password_hash, status_page.created_at FROM status_page WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "StatusPage.sq:selectPageById"
  }

  private inner class SelectPageBySlugQuery<out T : Any>(
    public val slug: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("status_page", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("status_page", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_590_043_759, """SELECT status_page.id, status_page.slug, status_page.title, status_page.description, status_page.icon, status_page.theme, status_page.published, status_page.show_tags, status_page.show_uptime_percentage, status_page.show_certificate_expiry, status_page.footer_text, status_page.accent_color, status_page.password_hash, status_page.created_at FROM status_page WHERE slug = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, slug)
    }

    override fun toString(): String = "StatusPage.sq:selectPageBySlug"
  }

  private inner class SelectGroupsQuery<out T : Any>(
    public val status_page_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("status_page_group", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("status_page_group", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_883_481_684, """SELECT status_page_group.id, status_page_group.status_page_id, status_page_group.name, status_page_group.weight FROM status_page_group WHERE status_page_id = ? ORDER BY weight ASC, id ASC""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, status_page_id)
    }

    override fun toString(): String = "StatusPage.sq:selectGroups"
  }

  private inner class SelectGroupMonitorsQuery<out T : Any>(
    public val group_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("status_page_group_monitor", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("status_page_group_monitor", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_385_005_704, """SELECT status_page_group_monitor.group_id, status_page_group_monitor.monitor_id, status_page_group_monitor.weight FROM status_page_group_monitor WHERE group_id = ? ORDER BY weight ASC""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, group_id)
    }

    override fun toString(): String = "StatusPage.sq:selectGroupMonitors"
  }

  private inner class SelectIncidentsQuery<out T : Any>(
    public val status_page_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("incident", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("incident", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_233_795_425, """SELECT incident.id, incident.status_page_id, incident.title, incident.content, incident.style, incident.pin, incident.created_at, incident.last_updated_at FROM incident WHERE status_page_id = ? ORDER BY created_at DESC""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, status_page_id)
    }

    override fun toString(): String = "StatusPage.sq:selectIncidents"
  }

  private inner class SelectPinnedIncidentQuery<out T : Any>(
    public val status_page_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("incident", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("incident", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-774_917_814, """SELECT incident.id, incident.status_page_id, incident.title, incident.content, incident.style, incident.pin, incident.created_at, incident.last_updated_at FROM incident WHERE status_page_id = ? AND pin = 1 ORDER BY created_at DESC LIMIT 1""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, status_page_id)
    }

    override fun toString(): String = "StatusPage.sq:selectPinnedIncident"
  }
}
