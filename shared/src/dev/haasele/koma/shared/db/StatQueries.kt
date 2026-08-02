package dev.haasele.koma.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class StatQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectDaily(
    monitor_id: Long,
    day_ms: Long,
    mapper: (
      monitor_id: Long,
      day_ms: Long,
      up: Long,
      down: Long,
      maintenance: Long,
      ping_sum: Double,
      ping_count: Long,
      ping_min: Double?,
      ping_max: Double?,
    ) -> T,
  ): Query<T> = SelectDailyQuery(monitor_id, day_ms) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getDouble(5)!!,
      cursor.getLong(6)!!,
      cursor.getDouble(7),
      cursor.getDouble(8)
    )
  }

  public fun selectDaily(monitor_id: Long, day_ms: Long): Query<Stat_daily> = selectDaily(monitor_id, day_ms, ::Stat_daily)

  public fun <T : Any> selectDay(
    monitor_id: Long,
    day_ms: Long,
    mapper: (
      monitor_id: Long,
      day_ms: Long,
      up: Long,
      down: Long,
      maintenance: Long,
      ping_sum: Double,
      ping_count: Long,
      ping_min: Double?,
      ping_max: Double?,
    ) -> T,
  ): Query<T> = SelectDayQuery(monitor_id, day_ms) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getDouble(5)!!,
      cursor.getLong(6)!!,
      cursor.getDouble(7),
      cursor.getDouble(8)
    )
  }

  public fun selectDay(monitor_id: Long, day_ms: Long): Query<Stat_daily> = selectDay(monitor_id, day_ms, ::Stat_daily)

  public fun <T : Any> aggregateSince(
    monitor_id: Long,
    day_ms: Long,
    mapper: (
      upCount: Long?,
      downCount: Long?,
      pingSum: Double?,
      pingCount: Long?,
    ) -> T,
  ): Query<T> = AggregateSinceQuery(monitor_id, day_ms) { cursor ->
    mapper(
      cursor.getLong(0),
      cursor.getLong(1),
      cursor.getDouble(2),
      cursor.getLong(3)
    )
  }

  public fun aggregateSince(monitor_id: Long, day_ms: Long): Query<AggregateSince> = aggregateSince(monitor_id, day_ms, ::AggregateSince)

  /**
   * @return The number of rows updated.
   */
  public fun insertDaily(
    monitor_id: Long,
    day_ms: Long,
    up: Long,
    down: Long,
    maintenance: Long,
    ping_sum: Double,
    ping_count: Long,
    ping_min: Double?,
    ping_max: Double?,
  ): QueryResult<Long> {
    val result = driver.execute(418_173_225, """
        |INSERT OR REPLACE INTO stat_daily (monitor_id, day_ms, up, down, maintenance, ping_sum, ping_count, ping_min, ping_max)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
          bindLong(parameterIndex++, day_ms)
          bindLong(parameterIndex++, up)
          bindLong(parameterIndex++, down)
          bindLong(parameterIndex++, maintenance)
          bindDouble(parameterIndex++, ping_sum)
          bindLong(parameterIndex++, ping_count)
          bindDouble(parameterIndex++, ping_min)
          bindDouble(parameterIndex++, ping_max)
        }
    notifyQueries(418_173_225) { emit ->
      emit("stat_daily")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteByMonitor(monitor_id: Long): QueryResult<Long> {
    val result = driver.execute(-1_203_150_495, """DELETE FROM stat_daily WHERE monitor_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
        }
    notifyQueries(-1_203_150_495) { emit ->
      emit("stat_daily")
    }
    return result
  }

  private inner class SelectDailyQuery<out T : Any>(
    public val monitor_id: Long,
    public val day_ms: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("stat_daily", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("stat_daily", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(736_776_390, """SELECT stat_daily.monitor_id, stat_daily.day_ms, stat_daily.up, stat_daily.down, stat_daily.maintenance, stat_daily.ping_sum, stat_daily.ping_count, stat_daily.ping_min, stat_daily.ping_max FROM stat_daily WHERE monitor_id = ? AND day_ms >= ? ORDER BY day_ms ASC""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, day_ms)
    }

    override fun toString(): String = "Stat.sq:selectDaily"
  }

  private inner class SelectDayQuery<out T : Any>(
    public val monitor_id: Long,
    public val day_ms: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("stat_daily", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("stat_daily", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(367_246_729, """SELECT stat_daily.monitor_id, stat_daily.day_ms, stat_daily.up, stat_daily.down, stat_daily.maintenance, stat_daily.ping_sum, stat_daily.ping_count, stat_daily.ping_min, stat_daily.ping_max FROM stat_daily WHERE monitor_id = ? AND day_ms = ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, day_ms)
    }

    override fun toString(): String = "Stat.sq:selectDay"
  }

  private inner class AggregateSinceQuery<out T : Any>(
    public val monitor_id: Long,
    public val day_ms: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("stat_daily", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("stat_daily", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-409_413_166, """
    |SELECT
    |    SUM(up) AS upCount,
    |    SUM(down) AS downCount,
    |    SUM(ping_sum) AS pingSum,
    |    SUM(ping_count) AS pingCount
    |FROM stat_daily WHERE monitor_id = ? AND day_ms >= ?
    """.trimMargin(), mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
      bindLong(parameterIndex++, day_ms)
    }

    override fun toString(): String = "Stat.sq:aggregateSince"
  }
}
