package dev.haasele.koma.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.haasele.koma.shared.db.KomaDatabase

class IosDatabaseDriverFactory(private val databaseName: String = "koma.db") : DatabaseDriverFactory {
    override fun create(): SqlDriver = NativeSqliteDriver(KomaDatabase.Schema, databaseName)
}
