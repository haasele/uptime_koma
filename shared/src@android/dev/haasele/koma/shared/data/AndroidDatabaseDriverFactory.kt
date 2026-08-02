package dev.haasele.koma.shared.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.haasele.koma.shared.db.KomaDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
    private val databaseName: String = "koma.db",
) : DatabaseDriverFactory {
    override fun create(): SqlDriver = AndroidSqliteDriver(KomaDatabase.Schema, context, databaseName)
}
