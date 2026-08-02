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

public class NotificationChannelQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (
    id: Long,
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = Query(347_396_308, arrayOf("notification_channel"), driver, "NotificationChannel.sq", "selectAll", "SELECT notification_channel.id, notification_channel.name, notification_channel.provider, notification_channel.config, notification_channel.active, notification_channel.is_default, notification_channel.created_at FROM notification_channel ORDER BY name ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!
    )
  }

  public fun selectAll(): Query<Notification_channel> = selectAll(::Notification_channel)

  public fun <T : Any> selectById(id: Long, mapper: (
    id: Long,
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = SelectByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!
    )
  }

  public fun selectById(id: Long): Query<Notification_channel> = selectById(id, ::Notification_channel)

  public fun <T : Any> selectDefaults(mapper: (
    id: Long,
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = Query(2_097_027_839, arrayOf("notification_channel"), driver, "NotificationChannel.sq", "selectDefaults", "SELECT notification_channel.id, notification_channel.name, notification_channel.provider, notification_channel.config, notification_channel.active, notification_channel.is_default, notification_channel.created_at FROM notification_channel WHERE is_default = 1 AND active = 1") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!
    )
  }

  public fun selectDefaults(): Query<Notification_channel> = selectDefaults(::Notification_channel)

  public fun <T : Any> selectForMonitor(monitor_id: Long, mapper: (
    id: Long,
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = SelectForMonitorQuery(monitor_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!
    )
  }

  public fun selectForMonitor(monitor_id: Long): Query<Notification_channel> = selectForMonitor(monitor_id, ::Notification_channel)

  public fun selectIdsForMonitor(monitor_id: Long): Query<Long> = SelectIdsForMonitorQuery(monitor_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(1_676_222_234, driver, "NotificationChannel.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertChannel(
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_557_528_999, """
        |INSERT INTO notification_channel (name, provider, config, active, is_default, created_at)
        |VALUES (?, ?, ?, ?, ?, ?)
        """.trimMargin(), 6) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, provider)
          bindString(parameterIndex++, config)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, is_default)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(-1_557_528_999) { emit ->
      emit("notification_channel")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateChannel(
    name: String,
    provider: String,
    config: String,
    active: Boolean,
    is_default: Boolean,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_752_995_401, """UPDATE notification_channel SET name = ?, provider = ?, config = ?, active = ?, is_default = ? WHERE id = ?""", 6) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, provider)
          bindString(parameterIndex++, config)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, is_default)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_752_995_401) { emit ->
      emit("notification_channel")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteChannel(id: Long): QueryResult<Long> {
    val result = driver.execute(-1_250_793_689, """DELETE FROM notification_channel WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_250_793_689) { emit ->
      emit("notification_channel")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun linkNotification(monitor_id: Long, notification_id: Long): QueryResult<Long> {
    val result = driver.execute(-372_371_114, """INSERT OR REPLACE INTO monitor_notification (monitor_id, notification_id) VALUES (?, ?)""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
          bindLong(parameterIndex++, notification_id)
        }
    notifyQueries(-372_371_114) { emit ->
      emit("monitor_notification")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unlinkForMonitor(monitor_id: Long): QueryResult<Long> {
    val result = driver.execute(1_510_775_317, """DELETE FROM monitor_notification WHERE monitor_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
        }
    notifyQueries(1_510_775_317) { emit ->
      emit("monitor_notification")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unlinkChannel(notification_id: Long): QueryResult<Long> {
    val result = driver.execute(-1_741_234_753, """DELETE FROM monitor_notification WHERE notification_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, notification_id)
        }
    notifyQueries(-1_741_234_753) { emit ->
      emit("monitor_notification")
    }
    return result
  }

  private inner class SelectByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("notification_channel", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("notification_channel", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-2_115_575_041, """SELECT notification_channel.id, notification_channel.name, notification_channel.provider, notification_channel.config, notification_channel.active, notification_channel.is_default, notification_channel.created_at FROM notification_channel WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "NotificationChannel.sq:selectById"
  }

  private inner class SelectForMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("notification_channel", "monitor_notification", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("notification_channel", "monitor_notification", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_685_178_946, """
    |SELECT n.id, n.name, n.provider, n.config, n.active, n.is_default, n.created_at FROM monitor_notification mn
    |INNER JOIN notification_channel n ON n.id = mn.notification_id
    |WHERE mn.monitor_id = ? AND n.active = 1
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
    }

    override fun toString(): String = "NotificationChannel.sq:selectForMonitor"
  }

  private inner class SelectIdsForMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("monitor_notification", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("monitor_notification", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_437_019_292, """SELECT notification_id FROM monitor_notification WHERE monitor_id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
    }

    override fun toString(): String = "NotificationChannel.sq:selectIdsForMonitor"
  }
}
