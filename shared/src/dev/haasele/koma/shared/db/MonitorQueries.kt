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

public class MonitorQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (
    id: Long,
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ) -> T): Query<T> = Query(514_497_490, arrayOf("monitor"), driver, "Monitor.sq", "selectAll", "SELECT monitor.id, monitor.name, monitor.type, monitor.active, monitor.parent_id, monitor.description, monitor.interval_seconds, monitor.retry_interval_seconds, monitor.resend_interval, monitor.max_retries, monitor.timeout_seconds, monitor.upside_down, monitor.push_token, monitor.proxy_id, monitor.weight, monitor.config, monitor.created_at, monitor.updated_at FROM monitor ORDER BY weight DESC, name ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getBoolean(3)!!,
      cursor.getLong(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getBoolean(11)!!,
      cursor.getString(12),
      cursor.getLong(13),
      cursor.getLong(14)!!,
      cursor.getString(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectAll(): Query<Monitor> = selectAll(::Monitor)

  public fun <T : Any> selectById(id: Long, mapper: (
    id: Long,
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ) -> T): Query<T> = SelectByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getBoolean(3)!!,
      cursor.getLong(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getBoolean(11)!!,
      cursor.getString(12),
      cursor.getLong(13),
      cursor.getLong(14)!!,
      cursor.getString(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectById(id: Long): Query<Monitor> = selectById(id, ::Monitor)

  public fun <T : Any> selectActive(mapper: (
    id: Long,
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ) -> T): Query<T> = Query(-1_351_523_531, arrayOf("monitor"), driver, "Monitor.sq", "selectActive", "SELECT monitor.id, monitor.name, monitor.type, monitor.active, monitor.parent_id, monitor.description, monitor.interval_seconds, monitor.retry_interval_seconds, monitor.resend_interval, monitor.max_retries, monitor.timeout_seconds, monitor.upside_down, monitor.push_token, monitor.proxy_id, monitor.weight, monitor.config, monitor.created_at, monitor.updated_at FROM monitor WHERE active = 1") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getBoolean(3)!!,
      cursor.getLong(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getBoolean(11)!!,
      cursor.getString(12),
      cursor.getLong(13),
      cursor.getLong(14)!!,
      cursor.getString(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectActive(): Query<Monitor> = selectActive(::Monitor)

  public fun <T : Any> selectByPushToken(push_token: String?, mapper: (
    id: Long,
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ) -> T): Query<T> = SelectByPushTokenQuery(push_token) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getBoolean(3)!!,
      cursor.getLong(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getBoolean(11)!!,
      cursor.getString(12),
      cursor.getLong(13),
      cursor.getLong(14)!!,
      cursor.getString(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectByPushToken(push_token: String?): Query<Monitor> = selectByPushToken(push_token, ::Monitor)

  public fun <T : Any> selectChildren(parent_id: Long?, mapper: (
    id: Long,
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ) -> T): Query<T> = SelectChildrenQuery(parent_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getBoolean(3)!!,
      cursor.getLong(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getBoolean(11)!!,
      cursor.getString(12),
      cursor.getLong(13),
      cursor.getLong(14)!!,
      cursor.getString(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectChildren(parent_id: Long?): Query<Monitor> = selectChildren(parent_id, ::Monitor)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(145_459_932, driver, "Monitor.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertMonitor(
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    created_at: Long,
    updated_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-2_079_370_578, """
        |INSERT INTO monitor (
        |    name, type, active, parent_id, description, interval_seconds, retry_interval_seconds,
        |    resend_interval, max_retries, timeout_seconds, upside_down, push_token, proxy_id,
        |    weight, config, created_at, updated_at
        |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 17) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, type)
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, parent_id)
          bindString(parameterIndex++, description)
          bindLong(parameterIndex++, interval_seconds)
          bindLong(parameterIndex++, retry_interval_seconds)
          bindLong(parameterIndex++, resend_interval)
          bindLong(parameterIndex++, max_retries)
          bindLong(parameterIndex++, timeout_seconds)
          bindBoolean(parameterIndex++, upside_down)
          bindString(parameterIndex++, push_token)
          bindLong(parameterIndex++, proxy_id)
          bindLong(parameterIndex++, weight)
          bindString(parameterIndex++, config)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, updated_at)
        }
    notifyQueries(-2_079_370_578) { emit ->
      emit("monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateMonitor(
    name: String,
    type: String,
    active: Boolean,
    parent_id: Long?,
    description: String?,
    interval_seconds: Long,
    retry_interval_seconds: Long,
    resend_interval: Long,
    max_retries: Long,
    timeout_seconds: Long,
    upside_down: Boolean,
    push_token: String?,
    proxy_id: Long?,
    weight: Long,
    config: String,
    updated_at: Long,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_231_153_822, """
        |UPDATE monitor SET
        |    name = ?, type = ?, active = ?, parent_id = ?, description = ?, interval_seconds = ?,
        |    retry_interval_seconds = ?, resend_interval = ?, max_retries = ?, timeout_seconds = ?,
        |    upside_down = ?, push_token = ?, proxy_id = ?, weight = ?, config = ?, updated_at = ?
        |WHERE id = ?
        """.trimMargin(), 17) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, type)
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, parent_id)
          bindString(parameterIndex++, description)
          bindLong(parameterIndex++, interval_seconds)
          bindLong(parameterIndex++, retry_interval_seconds)
          bindLong(parameterIndex++, resend_interval)
          bindLong(parameterIndex++, max_retries)
          bindLong(parameterIndex++, timeout_seconds)
          bindBoolean(parameterIndex++, upside_down)
          bindString(parameterIndex++, push_token)
          bindLong(parameterIndex++, proxy_id)
          bindLong(parameterIndex++, weight)
          bindString(parameterIndex++, config)
          bindLong(parameterIndex++, updated_at)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_231_153_822) { emit ->
      emit("monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun setActive(
    active: Boolean,
    updated_at: Long,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-2_006_018_347, """UPDATE monitor SET active = ?, updated_at = ? WHERE id = ?""", 3) {
          var parameterIndex = 0
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, updated_at)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-2_006_018_347) { emit ->
      emit("monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteMonitor(id: Long): QueryResult<Long> {
    val result = driver.execute(-1_772_635_268, """DELETE FROM monitor WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_772_635_268) { emit ->
      emit("monitor")
    }
    return result
  }

  private inner class SelectByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("monitor", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("monitor", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_230_405_695, """SELECT monitor.id, monitor.name, monitor.type, monitor.active, monitor.parent_id, monitor.description, monitor.interval_seconds, monitor.retry_interval_seconds, monitor.resend_interval, monitor.max_retries, monitor.timeout_seconds, monitor.upside_down, monitor.push_token, monitor.proxy_id, monitor.weight, monitor.config, monitor.created_at, monitor.updated_at FROM monitor WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "Monitor.sq:selectById"
  }

  private inner class SelectByPushTokenQuery<out T : Any>(
    public val push_token: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("monitor", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("monitor", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT monitor.id, monitor.name, monitor.type, monitor.active, monitor.parent_id, monitor.description, monitor.interval_seconds, monitor.retry_interval_seconds, monitor.resend_interval, monitor.max_retries, monitor.timeout_seconds, monitor.upside_down, monitor.push_token, monitor.proxy_id, monitor.weight, monitor.config, monitor.created_at, monitor.updated_at FROM monitor WHERE push_token ${ if (push_token == null) "IS" else "=" } ? AND type = 'push'""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, push_token)
    }

    override fun toString(): String = "Monitor.sq:selectByPushToken"
  }

  private inner class SelectChildrenQuery<out T : Any>(
    public val parent_id: Long?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("monitor", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("monitor", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT monitor.id, monitor.name, monitor.type, monitor.active, monitor.parent_id, monitor.description, monitor.interval_seconds, monitor.retry_interval_seconds, monitor.resend_interval, monitor.max_retries, monitor.timeout_seconds, monitor.upside_down, monitor.push_token, monitor.proxy_id, monitor.weight, monitor.config, monitor.created_at, monitor.updated_at FROM monitor WHERE parent_id ${ if (parent_id == null) "IS" else "=" } ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, parent_id)
    }

    override fun toString(): String = "Monitor.sq:selectChildren"
  }
}
