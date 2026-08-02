package dev.haasele.koma.shared.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class TagQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllTags(mapper: (
    id: Long,
    name: String,
    color: String,
  ) -> T): Query<T> = Query(-1_604_500_661, arrayOf("tag"), driver, "Tag.sq", "selectAllTags", "SELECT tag.id, tag.name, tag.color FROM tag ORDER BY name ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!
    )
  }

  public fun selectAllTags(): Query<Tag> = selectAllTags(::Tag)

  public fun <T : Any> selectTagById(id: Long, mapper: (
    id: Long,
    name: String,
    color: String,
  ) -> T): Query<T> = SelectTagByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!
    )
  }

  public fun selectTagById(id: Long): Query<Tag> = selectTagById(id, ::Tag)

  public fun <T : Any> selectTagsForMonitor(monitor_id: Long, mapper: (
    id: Long,
    name: String,
    color: String,
    value_: String?,
  ) -> T): Query<T> = SelectTagsForMonitorQuery(monitor_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)
    )
  }

  public fun selectTagsForMonitor(monitor_id: Long): Query<SelectTagsForMonitor> = selectTagsForMonitor(monitor_id, ::SelectTagsForMonitor)

  public fun <T : Any> selectAllMonitorTags(mapper: (
    monitor_id: Long,
    id: Long,
    name: String,
    color: String,
    value_: String?,
  ) -> T): Query<T> = Query(263_912_737, arrayOf("monitor_tag", "tag"), driver, "Tag.sq", "selectAllMonitorTags", """
  |SELECT mt.monitor_id, t.id, t.name, t.color, mt.value
  |FROM monitor_tag mt INNER JOIN tag t ON t.id = mt.tag_id
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)
    )
  }

  public fun selectAllMonitorTags(): Query<SelectAllMonitorTags> = selectAllMonitorTags(::SelectAllMonitorTags)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(236_018_332, driver, "Tag.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertTag(name: String, color: String): QueryResult<Long> {
    val result = driver.execute(-1_867_548_882, """INSERT INTO tag (name, color) VALUES (?, ?)""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, color)
        }
    notifyQueries(-1_867_548_882) { emit ->
      emit("tag")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateTag(
    name: String,
    color: String,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(862_684_958, """UPDATE tag SET name = ?, color = ? WHERE id = ?""", 3) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, color)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(862_684_958) { emit ->
      emit("tag")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteTag(id: Long): QueryResult<Long> {
    val result = driver.execute(-1_841_793_284, """DELETE FROM tag WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_841_793_284) { emit ->
      emit("tag")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteTagLinks(tag_id: Long): QueryResult<Long> {
    val result = driver.execute(520_545_821, """DELETE FROM monitor_tag WHERE tag_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, tag_id)
        }
    notifyQueries(520_545_821) { emit ->
      emit("monitor_tag")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun linkTag(
    monitor_id: Long,
    tag_id: Long,
    value_: String?,
  ): QueryResult<Long> {
    val result = driver.execute(872_732_045, """INSERT OR REPLACE INTO monitor_tag (monitor_id, tag_id, value) VALUES (?, ?, ?)""", 3) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
          bindLong(parameterIndex++, tag_id)
          bindString(parameterIndex++, value_)
        }
    notifyQueries(872_732_045) { emit ->
      emit("monitor_tag")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun unlinkTagsForMonitor(monitor_id: Long): QueryResult<Long> {
    val result = driver.execute(1_756_016_400, """DELETE FROM monitor_tag WHERE monitor_id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, monitor_id)
        }
    notifyQueries(1_756_016_400) { emit ->
      emit("monitor_tag")
    }
    return result
  }

  private inner class SelectTagByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("tag", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("tag", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(2_053_115_005, """SELECT tag.id, tag.name, tag.color FROM tag WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "Tag.sq:selectTagById"
  }

  private inner class SelectTagsForMonitorQuery<out T : Any>(
    public val monitor_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("tag", "monitor_tag", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("tag", "monitor_tag", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_825_288_647, """
    |SELECT t.id, t.name, t.color, mt.value
    |FROM monitor_tag mt INNER JOIN tag t ON t.id = mt.tag_id
    |WHERE mt.monitor_id = ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, monitor_id)
    }

    override fun toString(): String = "Tag.sq:selectTagsForMonitor"
  }
}
