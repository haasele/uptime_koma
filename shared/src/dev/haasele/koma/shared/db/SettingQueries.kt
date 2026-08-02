package dev.haasele.koma.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class SettingQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (key: String, value_: String) -> T): Query<T> = Query(-690_091_364, arrayOf("setting"), driver, "Setting.sq", "selectAll", "SELECT setting.key, setting.value FROM setting") { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!
    )
  }

  public fun selectAll(): Query<Setting> = selectAll(::Setting)

  public fun selectByKey(key: String): Query<String> = SelectByKeyQuery(key) { cursor ->
    cursor.getString(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun upsert(key: String, value_: String): QueryResult<Long> {
    val result = driver.execute(-1_131_026_184, """INSERT OR REPLACE INTO setting (key, value) VALUES (?, ?)""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, key)
          bindString(parameterIndex++, value_)
        }
    notifyQueries(-1_131_026_184) { emit ->
      emit("setting")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteByKey(key: String): QueryResult<Long> {
    val result = driver.execute(1_206_135_156, """DELETE FROM setting WHERE key = ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, key)
        }
    notifyQueries(1_206_135_156) { emit ->
      emit("setting")
    }
    return result
  }

  private inner class SelectByKeyQuery<out T : Any>(
    public val key: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("setting", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("setting", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_751_554_877, """SELECT value FROM setting WHERE key = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, key)
    }

    override fun toString(): String = "Setting.sq:selectByKey"
  }
}
