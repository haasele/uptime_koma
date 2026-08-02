package dev.haasele.koma.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.haasele.koma.shared.db.KomaDatabase
import java.io.File
import java.util.Properties

class JvmDatabaseDriverFactory(private val databaseFile: File) : DatabaseDriverFactory {

    override fun create(): SqlDriver {
        databaseFile.parentFile?.mkdirs()
        val properties = Properties().apply {
            // Applied on connect by sqlite-jdbc; also set via PRAGMA below for clarity.
            setProperty("busy_timeout", "30000")
        }
        val driver = SingleConnectionSqliteDriver(
            url = "jdbc:sqlite:${databaseFile.absolutePath}",
            properties = properties,
        )
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA busy_timeout=30000", 0)
        driver.execute(null, "PRAGMA synchronous=NORMAL", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)

        // The JDBC driver does not track the schema version itself, so the app owns
        // create-or-migrate based on SQLite's user_version.
        val currentVersion = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            parameters = 0,
        ).value

        val targetVersion = KomaDatabase.Schema.version
        when {
            currentVersion == 0L -> {
                KomaDatabase.Schema.create(driver)
                driver.execute(null, "PRAGMA user_version=$targetVersion", 0)
            }
            currentVersion < targetVersion -> {
                KomaDatabase.Schema.migrate(driver, currentVersion, targetVersion)
                driver.execute(null, "PRAGMA user_version=$targetVersion", 0)
            }
        }
        return driver
    }
}
