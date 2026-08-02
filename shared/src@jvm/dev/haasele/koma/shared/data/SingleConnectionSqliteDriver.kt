package dev.haasele.koma.shared.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One shared JDBC connection for the whole process, with a reentrant lock around every
 * statement and transaction.
 *
 * SQLDelight's stock [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver] uses a
 * ThreadLocal connection per worker thread. With [kotlinx.coroutines.Dispatchers.IO] that
 * means many concurrent writers on the same WAL file, which surfaces as
 * `SQLITE_BUSY_SNAPSHOT` during parallel monitor checks.
 */
internal class SingleConnectionSqliteDriver(
    url: String,
    properties: Properties = Properties(),
) : JdbcDriver() {
    private val connection: Connection = DriverManager.getConnection(url, properties)
    private val lock = ReentrantLock()
    private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

    override fun getConnection(): Connection = connection

    override fun closeConnection(connection: Connection) = Unit

    override fun close() {
        lock.withLock { this.connection.close() }
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = lock.withLock {
        retryBusy { super.execute(identifier, sql, parameters, binders) }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = lock.withLock {
        retryBusy { super.executeQuery(identifier, sql, mapper, parameters, binders) }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val outermost = currentTransaction() == null
        if (outermost) lock.lock()
        return try {
            super.newTransaction()
        } catch (error: Throwable) {
            if (outermost) lock.unlock()
            throw error
        }
    }

    override fun Connection.endTransaction() {
        try {
            commit()
            autoCommit = true
        } finally {
            lock.unlock()
        }
    }

    override fun Connection.rollbackTransaction() {
        try {
            rollback()
            autoCommit = true
        } finally {
            lock.unlock()
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { key ->
                listeners.getOrPut(key) { linkedSetOf() }.add(listener)
            }
        }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { key -> listeners[key]?.remove(listener) }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val toNotify = linkedSetOf<Query.Listener>()
        synchronized(listeners) {
            queryKeys.forEach { key -> listeners[key]?.let(toNotify::addAll) }
        }
        toNotify.forEach(Query.Listener::queryResultsChanged)
    }

    private fun <T> retryBusy(block: () -> T): T {
        var last: Throwable? = null
        repeat(8) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                last = error
                if (!isSqliteBusy(error) || attempt == 7) throw error
                Thread.sleep(25L * (attempt + 1))
            }
        }
        throw last ?: error("retryBusy exhausted")
    }

    private fun isSqliteBusy(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .uppercase()
        return message.contains("SQLITE_BUSY") ||
            message.contains("DATABASE IS LOCKED") ||
            message.contains("BUSY_SNAPSHOT")
    }
}
