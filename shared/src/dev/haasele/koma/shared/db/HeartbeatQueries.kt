package dev.haasele.koma.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.String

public class HeartbeatQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> recentByMonitor(
    monitor_id: Long,
    `value`: Long,
    mapper: (
      id: Long,
      monitor_id: Long,
      status: Long,
      msg: String,
      ping_ms: Long?,
      important: Boolean,
      time_ms: Long,
      duration_seconds: Long,
      retries: Long,
      down_count: Long,
    ) -> T,
  ): Query<T> = RecentByMonitorQuery(monitor_id, value) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun recentByMonitor(monitor_id: Long, value_: Long): Query<Heartbeat> = recentByMonitor(monitor_id, value_, ::Heartbeat)

  public fun <T : Any> lastByMonitor(monitor_id: Long, mapper: (
    id: Long,
    monitor_id: Long,
    status: Long,
    msg: String,
    ping_ms: Long?,
    important: Boolean,
    time_ms: Long,
    duration_seconds: Long,
    retries: Long,
    down_count: Long,
  ) -> T): Query<T> = LastByMonitorQuery(monitor_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun lastByMonitor(monitor_id: Long): Query<Heartbeat> = lastByMonitor(monitor_id, ::Heartbeat)

  public fun <T : Any> lastImportantByMonitor(monitor_id: Long, mapper: (
    id: Long,
    monitor_id: Long,
    status: Long,
    msg: String,
    ping_ms: Long?,
    important: Boolean,
    time_ms: Long,
    duration_seconds: Long,
    retries: Long,
    down_count: Long,
  ) -> T): Query<T> = LastImportantByMonitorQuery(monitor_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun lastImportantByMonitor(monitor_id: Long): Query<Heartbeat> = lastImportantByMonitor(monitor_id, ::Heartbeat)

  public fun <T : Any> sinceByMonitor(
    monitor_id: Long,
    time_ms: Long,
    mapper: (
      id: Long,
      monitor_id: Long,
      status: Long,
      msg: String,
      ping_ms: Long?,
      important: Boolean,
      time_ms: Long,
      duration_seconds: Long,
      retries: Long,
      down_count: Long,
    ) -> T,
  ): Query<T> = SinceByMonitorQuery(monitor_id, time_ms) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun sinceByMonitor(monitor_id: Long, time_ms: Long): Query<Heartbeat> = sinceByMonitor(monitor_id, time_ms, ::Heartbeat)

  public fun <T : Any> importantByMonitor(
    monitor_id: Long,
    `value`: Long,
    mapper: (
      id: Long,
      monitor_id: Long,
      status: Long,
      msg: String,
      ping_ms: Long?,
      important: Boolean,
      time_ms: Long,
      duration_seconds: Long,
      retries: Long,
      down_count: Long,
    ) -> T,
  ): Query<T> = ImportantByMonitorQuery(monitor_id, value) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun importantByMonitor(monitor_id: Long, value_: Long): Query<Heartbeat> = importantByMonitor(monitor_id, value_, ::Heartbeat)

  public fun <T : Any> latestPerMonitor(mapper: (
    id: Long,
    monitor_id: Long,
    status: Long,
    msg: String,
    ping_ms: Long?,
    important: Boolean,
    time_ms: Long,
    duration_seconds: Long,
    retries: Long,
    down_count: Long,
  ) -> T): Query<T> = Query(1_749_651_161, arrayOf("heartbeat"), driver, "Heartbeat.sq", "latestPerMonitor", """
  |SELECT h.id, h.monitor_id, h.status, h.msg, h.ping_ms, h.important, h.time_ms, h.duration_seconds, h.retries, h.down_count FROM heartbeat h
  |INNER JOIN (
  |    SELECT monitor_id, MAX(time_ms) AS max_time FROM heartbeat GROUP BY monitor_id
  |) m ON h.monitor_id = m.monitor_id AND h.time_ms = m.max_time
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4),
      cursor.getBoolean(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun latestPerMonitor(): Query<Heartbeat> = latestPerMonitor(::Heartbeat)

  public fun <T : Any> uptimeSince(
    monitor_id: Long,
    time_ms: Long,
    mapper: (
      upCount: Long?,
      downCount: Long?,
      upSeconds: Long?,
      downSeconds: Long?,
      avgPing: Double?,
    ) -> T,
  ): Query<T> = UptimeSinceQuery(monitor_id, time_ms) { cursor ->
    mapper(
      cursor.getLong(0),
      cursor.getLong(1),
      cursor.getLong(2),
      cursor.getLong(3),
      cursor.getDouble(4)
    )
  }

  public fun uptimeSince(monitor_id: Long, time_ms: Long): Query<UptimeSince> = uptimeSince(monitor_id, time_ms, ::UptimeSince)

  public fun countAll(): Query<Long> = Query(-1_478_349_433, arrayOf("heartbeat"), driver, "Heartbeat.sq", "countAll", "SELECT count(*) FROM heartbeat") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertHeartbeat(
    monitor_id: Long,
    status: Long,
    msg: String,
    ping_ms: Long?,
    important: Boolean,
    time_ms: Long,
    duration_seconds: Long,
    retries: Long,
    down_count: Long,
  ): QueryResult<Long> {
    val result = driver.execute(770_155_822, """
        |INSERT INTO heartbeat (monitor_id, status, msg, ping_ms, important, time_ms, duration_seconds, retries, down_count)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
          bindLong(parameterIndex++, status)
          bindString(parameterIndex++, msg)
          bindLong(parameterIndex++, ping_ms)
          bindBoolean(parameterIndex++, important)
          bindLong(parameterIndex++, time_ms)
          bindLong(parameterIndex++, duration_seconds)
          bindLong(parameterIndex++, retries)
          bindLong(parameterIndex++, down_count)
        }
    notifyQueries(770_155_822) { emit ->
      emit("heartbeat")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteByMonitor(monitor_id: Long): QueryResult<Long> {
    val result = driver.execute(1_279_896_195, """DELETE FROM heartbeat WHERE monitor_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
        }
    notifyQueries(1_279_896_195) { emit ->
      emit("heartbeat")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteOlderThan(time_ms: Long): QueryResult<Long> {
    val result = driver.execute(1_025_607_253, """DELETE FROM heartbeat WHERE time_ms < ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, time_ms)
        }
    notifyQueries(1_025_607_253) { emit ->
      emit("heartbeat")
    }
    return result
  }

  private inner class RecentByMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_499_484_205, """SELECT heartbeat.id, heartbeat.monitor_id, heartbeat.status, heartbeat.msg, heartbeat.ping_ms, heartbeat.important, heartbeat.time_ms, heartbeat.duration_seconds, heartbeat.retries, heartbeat.down_count FROM heartbeat WHERE monitor_id = ? ORDER BY time_ms DESC LIMIT ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "Heartbeat.sq:recentByMonitor"
  }

  private inner class LastByMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_811_630_920, """SELECT heartbeat.id, heartbeat.monitor_id, heartbeat.status, heartbeat.msg, heartbeat.ping_ms, heartbeat.important, heartbeat.time_ms, heartbeat.duration_seconds, heartbeat.retries, heartbeat.down_count FROM heartbeat WHERE monitor_id = ? ORDER BY time_ms DESC LIMIT 1""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
    }

    override fun toString(): String = "Heartbeat.sq:lastByMonitor"
  }

  private inner class LastImportantByMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-530_925_844, """SELECT heartbeat.id, heartbeat.monitor_id, heartbeat.status, heartbeat.msg, heartbeat.ping_ms, heartbeat.important, heartbeat.time_ms, heartbeat.duration_seconds, heartbeat.retries, heartbeat.down_count FROM heartbeat WHERE monitor_id = ? AND important = 1 ORDER BY time_ms DESC LIMIT 1""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
    }

    override fun toString(): String = "Heartbeat.sq:lastImportantByMonitor"
  }

  private inner class SinceByMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    public val time_ms: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-444_639_586, """SELECT heartbeat.id, heartbeat.monitor_id, heartbeat.status, heartbeat.msg, heartbeat.ping_ms, heartbeat.important, heartbeat.time_ms, heartbeat.duration_seconds, heartbeat.retries, heartbeat.down_count FROM heartbeat WHERE monitor_id = ? AND time_ms >= ? ORDER BY time_ms ASC""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, time_ms)
    }

    override fun toString(): String = "Heartbeat.sq:sinceByMonitor"
  }

  private inner class ImportantByMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_605_680_074, """SELECT heartbeat.id, heartbeat.monitor_id, heartbeat.status, heartbeat.msg, heartbeat.ping_ms, heartbeat.important, heartbeat.time_ms, heartbeat.duration_seconds, heartbeat.retries, heartbeat.down_count FROM heartbeat WHERE monitor_id = ? AND important = 1 ORDER BY time_ms DESC LIMIT ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "Heartbeat.sq:importantByMonitor"
  }

  private inner class UptimeSinceQuery<out T : Any>(
    public val monitor_id: Long,
    public val time_ms: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("heartbeat", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("heartbeat", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_678_154_909, """
    |SELECT
    |    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS upCount,
    |    SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS downCount,
    |    SUM(CASE WHEN status = 1 THEN duration_seconds ELSE 0 END) AS upSeconds,
    |    SUM(CASE WHEN status = 0 THEN duration_seconds ELSE 0 END) AS downSeconds,
    |    AVG(CASE WHEN status = 1 THEN ping_ms END) AS avgPing
    |FROM heartbeat WHERE monitor_id = ? AND time_ms >= ?
    """.trimMargin(), mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, time_ms)
    }

    override fun toString(): String = "Heartbeat.sq:uptimeSince"
  }
}
