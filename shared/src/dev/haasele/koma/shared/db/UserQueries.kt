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

public class UserQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun countUsers(): Query<Long> = Query(-702_980_089, arrayOf("user"), driver, "User.sq", "countUsers", "SELECT count(*) FROM user") { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectByUsername(username: String, mapper: (
    id: Long,
    username: String,
    password_hash: String,
    twofa_secret: String?,
    twofa_enabled: Boolean,
    created_at: Long,
  ) -> T): Query<T> = SelectByUsernameQuery(username) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getBoolean(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectByUsername(username: String): Query<User> = selectByUsername(username, ::User)

  public fun <T : Any> selectById(id: Long, mapper: (
    id: Long,
    username: String,
    password_hash: String,
    twofa_secret: String?,
    twofa_enabled: Boolean,
    created_at: Long,
  ) -> T): Query<T> = SelectByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getBoolean(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectById(id: Long): Query<User> = selectById(id, ::User)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(821_886_551, driver, "User.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertUser(
    username: String,
    password_hash: String,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_307_631_602, """INSERT INTO user (username, password_hash, created_at) VALUES (?, ?, ?)""", 3) {
          var parameterIndex = 0
          bindString(parameterIndex++, username)
          bindString(parameterIndex++, password_hash)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(1_307_631_602) { emit ->
      emit("user")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePassword(password_hash: String, id: Long): QueryResult<Long> {
    val result = driver.execute(1_733_273_938, """UPDATE user SET password_hash = ? WHERE id = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, password_hash)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_733_273_938) { emit ->
      emit("user")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateUsername(username: String, id: Long): QueryResult<Long> {
    val result = driver.execute(250_574_733, """UPDATE user SET username = ? WHERE id = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, username)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(250_574_733) { emit ->
      emit("user")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateTwoFactor(
    twofa_secret: String?,
    twofa_enabled: Boolean,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_619_876_124, """UPDATE user SET twofa_secret = ?, twofa_enabled = ? WHERE id = ?""", 3) {
          var parameterIndex = 0
          bindString(parameterIndex++, twofa_secret)
          bindBoolean(parameterIndex++, twofa_enabled)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_619_876_124) { emit ->
      emit("user")
    }
    return result
  }

  private inner class SelectByUsernameQuery<out T : Any>(
    public val username: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("user", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("user", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_904_371_639, """SELECT user.id, user.username, user.password_hash, user.twofa_secret, user.twofa_enabled, user.created_at FROM user WHERE username = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, username)
    }

    override fun toString(): String = "User.sq:selectByUsername"
  }

  private inner class SelectByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("user", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("user", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_315_051_332, """SELECT user.id, user.username, user.password_hash, user.twofa_secret, user.twofa_enabled, user.created_at FROM user WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "User.sq:selectById"
  }
}
