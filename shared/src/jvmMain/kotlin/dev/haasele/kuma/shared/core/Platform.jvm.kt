package dev.haasele.koma.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

actual object Platform {
    actual val kind: PlatformKind = PlatformKind.DESKTOP
    actual val name: String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    actual val supportsLongRunningEngine: Boolean = true
    actual val supportsIcmp: Boolean = true
    actual val supportsEmbeddedServer: Boolean = true
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Desktop data directory, following the platform conventions for user data. */
object DesktopPaths {
    val dataDirectory: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = File(System.getProperty("user.home"))
        val base = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").path)
            os.contains("mac") -> home.resolve("Library/Application Support")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
        }
        base.resolve("koma-native").also { it.mkdirs() }
    }

    val databaseFile: File get() = dataDirectory.resolve("koma.db")
}
