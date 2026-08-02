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

public class MaintenanceQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (
    id: Long,
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    created_at: Long,
  ) -> T): Query<T> = Query(2_038_155_321, arrayOf("maintenance"), driver, "Maintenance.sq", "selectAll", "SELECT maintenance.id, maintenance.title, maintenance.description, maintenance.strategy, maintenance.active, maintenance.manual_active, maintenance.timezone, maintenance.start_ms, maintenance.end_ms, maintenance.start_time, maintenance.end_time, maintenance.weekdays, maintenance.days_of_month, maintenance.cron, maintenance.duration_minutes, maintenance.interval_day, maintenance.created_at FROM maintenance ORDER BY created_at DESC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getString(6),
      cursor.getLong(7),
      cursor.getLong(8),
      cursor.getString(9),
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getString(13),
      cursor.getLong(14),
      cursor.getLong(15),
      cursor.getLong(16)!!
    )
  }

  public fun selectAll(): Query<Maintenance> = selectAll(::Maintenance)

  public fun <T : Any> selectById(id: Long, mapper: (
    id: Long,
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    created_at: Long,
  ) -> T): Query<T> = SelectByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getString(6),
      cursor.getLong(7),
      cursor.getLong(8),
      cursor.getString(9),
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getString(13),
      cursor.getLong(14),
      cursor.getLong(15),
      cursor.getLong(16)!!
    )
  }

  public fun selectById(id: Long): Query<Maintenance> = selectById(id, ::Maintenance)

  public fun <T : Any> selectActive(mapper: (
    id: Long,
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    created_at: Long,
  ) -> T): Query<T> = Query(724_535_662, arrayOf("maintenance"), driver, "Maintenance.sq", "selectActive", "SELECT maintenance.id, maintenance.title, maintenance.description, maintenance.strategy, maintenance.active, maintenance.manual_active, maintenance.timezone, maintenance.start_ms, maintenance.end_ms, maintenance.start_time, maintenance.end_time, maintenance.weekdays, maintenance.days_of_month, maintenance.cron, maintenance.duration_minutes, maintenance.interval_day, maintenance.created_at FROM maintenance WHERE active = 1") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getString(6),
      cursor.getLong(7),
      cursor.getLong(8),
      cursor.getString(9),
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getString(13),
      cursor.getLong(14),
      cursor.getLong(15),
      cursor.getLong(16)!!
    )
  }

  public fun selectActive(): Query<Maintenance> = selectActive(::Maintenance)

  public fun selectMonitorIds(maintenance_id: Long): Query<Long> = SelectMonitorIdsQuery(maintenance_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectAllMonitorLinks(mapper: (maintenance_id: Long, monitor_id: Long) -> T): Query<T> = Query(20_543_832, arrayOf("maintenance_monitor"), driver, "Maintenance.sq", "selectAllMonitorLinks", "SELECT maintenance_monitor.maintenance_id, maintenance_monitor.monitor_id FROM maintenance_monitor") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!
    )
  }

  public fun selectAllMonitorLinks(): Query<Maintenance_monitor> = selectAllMonitorLinks(::Maintenance_monitor)

  public fun selectStatusPageIds(maintenance_id: Long): Query<Long> = SelectStatusPageIdsQuery(maintenance_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectForStatusPage(status_page_id: Long, mapper: (
    id: Long,
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    created_at: Long,
  ) -> T): Query<T> = SelectForStatusPageQuery(status_page_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getBoolean(5)!!,
      cursor.getString(6),
      cursor.getLong(7),
      cursor.getLong(8),
      cursor.getString(9),
      cursor.getString(10),
      cursor.getString(11),
      cursor.getString(12),
      cursor.getString(13),
      cursor.getLong(14),
      cursor.getLong(15),
      cursor.getLong(16)!!
    )
  }

  public fun selectForStatusPage(status_page_id: Long): Query<Maintenance> = selectForStatusPage(status_page_id, ::Maintenance)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(-1_921_448_235, driver, "Maintenance.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertMaintenance(
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_962_372_146, """
        |INSERT INTO maintenance (title, description, strategy, active, manual_active, timezone,
        |    start_ms, end_ms, start_time, end_time, weekdays, days_of_month, cron, duration_minutes,
        |    interval_day, created_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 16) {
          var parameterIndex = 0
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, description)
          bindString(parameterIndex++, strategy)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, manual_active)
          bindString(parameterIndex++, timezone)
          bindLong(parameterIndex++, start_ms)
          bindLong(parameterIndex++, end_ms)
          bindString(parameterIndex++, start_time)
          bindString(parameterIndex++, end_time)
          bindString(parameterIndex++, weekdays)
          bindString(parameterIndex++, days_of_month)
          bindString(parameterIndex++, cron)
          bindLong(parameterIndex++, duration_minutes)
          bindLong(parameterIndex++, interval_day)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(-1_962_372_146) { emit ->
      emit("maintenance")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateMaintenance(
    title: String,
    description: String?,
    strategy: String,
    active: Boolean,
    manual_active: Boolean,
    timezone: String?,
    start_ms: Long?,
    end_ms: Long?,
    start_time: String?,
    end_time: String?,
    weekdays: String?,
    days_of_month: String?,
    cron: String?,
    duration_minutes: Long?,
    interval_day: Long?,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_267_878_978, """
        |UPDATE maintenance SET title = ?, description = ?, strategy = ?, active = ?, manual_active = ?,
        |    timezone = ?, start_ms = ?, end_ms = ?, start_time = ?, end_time = ?, weekdays = ?,
        |    days_of_month = ?, cron = ?, duration_minutes = ?, interval_day = ? WHERE id = ?
        """.trimMargin(), 16) {
          var parameterIndex = 0
          bindString(parameterIndex++, title)
          bindString(parameterIndex++, description)
          bindString(parameterIndex++, strategy)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, manual_active)
          bindString(parameterIndex++, timezone)
          bindLong(parameterIndex++, start_ms)
          bindLong(parameterIndex++, end_ms)
          bindString(parameterIndex++, start_time)
          bindString(parameterIndex++, end_time)
          bindString(parameterIndex++, weekdays)
          bindString(parameterIndex++, days_of_month)
          bindString(parameterIndex++, cron)
          bindLong(parameterIndex++, duration_minutes)
          bindLong(parameterIndex++, interval_day)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_267_878_978) { emit ->
      emit("maintenance")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun setManualActive(manual_active: Boolean, id: Long): QueryResult<Long> {
    val result = driver.execute(-897_209_822, """UPDATE maintenance SET manual_active = ? WHERE id = ?""", 2) {
          var parameterIndex = 0
          bindBoolean(parameterIndex++, manual_active)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-897_209_822) { emit ->
      emit("maintenance")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun setActive(active: Boolean, id: Long): QueryResult<Long> {
    val result = driver.execute(-482_360_516, """UPDATE maintenance SET active = ? WHERE id = ?""", 2) {
          var parameterIndex = 0
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-482_360_516) { emit ->
      emit("maintenance")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteMaintenance(id: Long): QueryResult<Long> {
    val result = driver.execute(-30_153_316, """DELETE FROM maintenance WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-30_153_316) { emit ->
      emit("maintenance")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun linkMonitor(maintenance_id: Long, monitor_id: Long): QueryResult<Long> {
    val result = driver.execute(-830_011_340, """INSERT OR REPLACE INTO maintenance_monitor (maintenance_id, monitor_id) VALUES (?, ?)""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, maintenance_id)
          bindLong(parameterIndex++, monitor_id)
        }
    notifyQueries(-830_011_340) { emit ->
      emit("maintenance_monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unlinkMonitors(maintenance_id: Long): QueryResult<Long> {
    val result = driver.execute(792_169_688, """DELETE FROM maintenance_monitor WHERE maintenance_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, maintenance_id)
        }
    notifyQueries(792_169_688) { emit ->
      emit("maintenance_monitor")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun linkStatusPage(maintenance_id: Long, status_page_id: Long): QueryResult<Long> {
    val result = driver.execute(1_949_756_551, """INSERT OR REPLACE INTO maintenance_status_page (maintenance_id, status_page_id) VALUES (?, ?)""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, maintenance_id)
          bindLong(parameterIndex++, status_page_id)
        }
    notifyQueries(1_949_756_551) { emit ->
      emit("maintenance_status_page")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unlinkStatusPages(maintenance_id: Long): QueryResult<Long> {
    val result = driver.execute(490_845_203, """DELETE FROM maintenance_status_page WHERE maintenance_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, maintenance_id)
        }
    notifyQueries(490_845_203) { emit ->
      emit("maintenance_status_page")
    }
    return result
  }

  private inner class SelectByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("maintenance", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("maintenance", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_241_653_190, """SELECT maintenance.id, maintenance.title, maintenance.description, maintenance.strategy, maintenance.active, maintenance.manual_active, maintenance.timezone, maintenance.start_ms, maintenance.end_ms, maintenance.start_time, maintenance.end_time, maintenance.weekdays, maintenance.days_of_month, maintenance.cron, maintenance.duration_minutes, maintenance.interval_day, maintenance.created_at FROM maintenance WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "Maintenance.sq:selectById"
  }

  private inner class SelectMonitorIdsQuery<out T : Any>(
    public val maintenance_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("maintenance_monitor", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("maintenance_monitor", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-2_130_132_634, """SELECT monitor_id FROM maintenance_monitor WHERE maintenance_id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, maintenance_id)
    }

    override fun toString(): String = "Maintenance.sq:selectMonitorIds"
  }

  private inner class SelectStatusPageIdsQuery<out T : Any>(
    public val maintenance_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("maintenance_status_page", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("maintenance_status_page", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-582_630_929, """SELECT status_page_id FROM maintenance_status_page WHERE maintenance_id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, maintenance_id)
    }

    override fun toString(): String = "Maintenance.sq:selectStatusPageIds"
  }

  private inner class SelectForStatusPageQuery<out T : Any>(
    public val status_page_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("maintenance", "maintenance_status_page", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("maintenance", "maintenance_status_page", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_327_099_934, """
    |SELECT m.id, m.title, m.description, m.strategy, m.active, m.manual_active, m.timezone, m.start_ms, m.end_ms, m.start_time, m.end_time, m.weekdays, m.days_of_month, m.cron, m.duration_minutes, m.interval_day, m.created_at FROM maintenance_status_page mp
    |INNER JOIN maintenance m ON m.id = mp.maintenance_id
    |WHERE mp.status_page_id = ? AND m.active = 1
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, status_page_id)
    }

    override fun toString(): String = "Maintenance.sq:selectForStatusPage"
  }
}
