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

public class InfrastructureQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectProxies(mapper: (
    id: Long,
    protocol: String,
    host: String,
    port: Long,
    username: String?,
    password: String?,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = Query(583_522_762, arrayOf("proxy"), driver, "Infrastructure.sq", "selectProxies", "SELECT proxy.id, proxy.protocol, proxy.host, proxy.port, proxy.username, proxy.password, proxy.active, proxy.is_default, proxy.created_at FROM proxy ORDER BY host ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4),
      cursor.getString(5),
      cursor.getBoolean(6)!!,
      cursor.getBoolean(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun selectProxies(): Query<Proxy> = selectProxies(::Proxy)

  public fun <T : Any> selectProxyById(id: Long, mapper: (
    id: Long,
    protocol: String,
    host: String,
    port: Long,
    username: String?,
    password: String?,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ) -> T): Query<T> = SelectProxyByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4),
      cursor.getString(5),
      cursor.getBoolean(6)!!,
      cursor.getBoolean(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun selectProxyById(id: Long): Query<Proxy> = selectProxyById(id, ::Proxy)

  public fun <T : Any> selectDockerHosts(mapper: (
    id: Long,
    name: String,
    connection_type: String,
    daemon: String,
    created_at: Long,
  ) -> T): Query<T> = Query(-1_819_998_007, arrayOf("docker_host"), driver, "Infrastructure.sq", "selectDockerHosts", "SELECT docker_host.id, docker_host.name, docker_host.connection_type, docker_host.daemon, docker_host.created_at FROM docker_host ORDER BY name ASC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun selectDockerHosts(): Query<Docker_host> = selectDockerHosts(::Docker_host)

  public fun <T : Any> selectDockerHostById(id: Long, mapper: (
    id: Long,
    name: String,
    connection_type: String,
    daemon: String,
    created_at: Long,
  ) -> T): Query<T> = SelectDockerHostByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun selectDockerHostById(id: Long): Query<Docker_host> = selectDockerHostById(id, ::Docker_host)

  public fun <T : Any> selectApiKeys(mapper: (
    id: Long,
    name: String,
    key_hash: String,
    prefix: String,
    active: Boolean,
    expires_at: Long?,
    created_at: Long,
  ) -> T): Query<T> = Query(91_726_188, arrayOf("api_key"), driver, "Infrastructure.sq", "selectApiKeys", "SELECT api_key.id, api_key.name, api_key.key_hash, api_key.prefix, api_key.active, api_key.expires_at, api_key.created_at FROM api_key ORDER BY created_at DESC") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getBoolean(4)!!,
      cursor.getLong(5),
      cursor.getLong(6)!!
    )
  }

  public fun selectApiKeys(): Query<Api_key> = selectApiKeys(::Api_key)

  public fun lastInsertedId(): ExecutableQuery<Long> = Query(2_118_458_351, driver, "Infrastructure.sq", "lastInsertedId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertProxy(
    protocol: String,
    host: String,
    port: Long,
    username: String?,
    password: String?,
    active: Boolean,
    is_default: Boolean,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(575_857_807, """
        |INSERT INTO proxy (protocol, host, port, username, password, active, is_default, created_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          var parameterIndex = 0
          bindString(parameterIndex++, protocol)
          bindString(parameterIndex++, host)
          bindLong(parameterIndex++, port)
          bindString(parameterIndex++, username)
          bindString(parameterIndex++, password)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, is_default)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(575_857_807) { emit ->
      emit("proxy")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateProxy(
    protocol: String,
    host: String,
    port: Long,
    username: String?,
    password: String?,
    active: Boolean,
    is_default: Boolean,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(105_560_191, """
        |UPDATE proxy SET protocol = ?, host = ?, port = ?, username = ?, password = ?, active = ?, is_default = ?
        |WHERE id = ?
        """.trimMargin(), 8) {
          var parameterIndex = 0
          bindString(parameterIndex++, protocol)
          bindString(parameterIndex++, host)
          bindLong(parameterIndex++, port)
          bindString(parameterIndex++, username)
          bindString(parameterIndex++, password)
          bindBoolean(parameterIndex++, active)
          bindBoolean(parameterIndex++, is_default)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(105_560_191) { emit ->
      emit("proxy")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteProxy(id: Long): QueryResult<Long> {
    val result = driver.execute(-442_816_291, """DELETE FROM proxy WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-442_816_291) { emit ->
      emit("proxy")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertDockerHost(
    name: String,
    connection_type: String,
    daemon: String,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(-1_414_866_329, """INSERT INTO docker_host (name, connection_type, daemon, created_at) VALUES (?, ?, ?, ?)""", 4) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, connection_type)
          bindString(parameterIndex++, daemon)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(-1_414_866_329) { emit ->
      emit("docker_host")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateDockerHost(
    name: String,
    connection_type: String,
    daemon: String,
    id: Long,
  ): QueryResult<Long> {
    val result = driver.execute(1_378_483_319, """UPDATE docker_host SET name = ?, connection_type = ?, daemon = ? WHERE id = ?""", 4) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, connection_type)
          bindString(parameterIndex++, daemon)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_378_483_319) { emit ->
      emit("docker_host")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteDockerHost(id: Long): QueryResult<Long> {
    val result = driver.execute(1_141_315_289, """DELETE FROM docker_host WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(1_141_315_289) { emit ->
      emit("docker_host")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertApiKey(
    name: String,
    key_hash: String,
    prefix: String,
    active: Boolean,
    expires_at: Long?,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(240_216_036, """INSERT INTO api_key (name, key_hash, prefix, active, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)""", 6) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, key_hash)
          bindString(parameterIndex++, prefix)
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, expires_at)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(240_216_036) { emit ->
      emit("api_key")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun setApiKeyActive(active: Boolean, id: Long): QueryResult<Long> {
    val result = driver.execute(-2_032_452_825, """UPDATE api_key SET active = ? WHERE id = ?""", 2) {
          var parameterIndex = 0
          bindBoolean(parameterIndex++, active)
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-2_032_452_825) { emit ->
      emit("api_key")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteApiKey(id: Long): QueryResult<Long> {
    val result = driver.execute(-1_273_909_930, """DELETE FROM api_key WHERE id = ?""", 1) {
          var parameterIndex = 0
          bindLong(parameterIndex++, id)
        }
    notifyQueries(-1_273_909_930) { emit ->
      emit("api_key")
    }
    return result
  }

  private inner class SelectProxyByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("proxy", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("proxy", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_861_599_714, """SELECT proxy.id, proxy.protocol, proxy.host, proxy.port, proxy.username, proxy.password, proxy.active, proxy.is_default, proxy.created_at FROM proxy WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "Infrastructure.sq:selectProxyById"
  }

  private inner class SelectDockerHostByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("docker_host", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("docker_host", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(105_177_052, """SELECT docker_host.id, docker_host.name, docker_host.connection_type, docker_host.daemon, docker_host.created_at FROM docker_host WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "Infrastructure.sq:selectDockerHostById"
  }
}
