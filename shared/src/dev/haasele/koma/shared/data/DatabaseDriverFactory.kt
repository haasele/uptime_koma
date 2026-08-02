package dev.haasele.koma.shared.data

import app.cash.sqldelight.db.SqlDriver
import dev.haasele.koma.shared.db.KomaDatabase

/**
 * Implemented per platform because each SQLite driver needs different inputs
 * (a file path on desktop, a Context on Android, a database name on iOS).
 */
interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): KomaDatabase = KomaDatabase(factory.create())
