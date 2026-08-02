package dev.haasele.koma.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

actual object Platform {
    actual val kind: PlatformKind = PlatformKind.DESKTOP
    actual val name: String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    actual val supportsLongRunningEngine: Boolean = true
    actual val supportsIcmp: Boolean = true
    actual val supportsEmbeddedServer: Boolean = true
    actual val localHostNames: List<String>
        get() = discoverLocalHostNames()
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

internal fun discoverLocalHostNames(): List<String> {
    val names = linkedSetOf<String>()
    runCatching {
        val local = InetAddress.getLocalHost()
        local.hostName?.takeIf { it.isNotBlank() && it != "localhost" }?.let(names::add)
        local.canonicalHostName?.takeIf { it.isNotBlank() && it != "localhost" }?.let(names::add)
    }
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nic ->
            if (!nic.isUp || nic.isLoopback) return@forEach
            nic.inetAddresses.toList().forEach { addr ->
                if (addr.isLoopbackAddress || addr.isLinkLocalAddress) return@forEach
                val host = addr.hostName?.takeIf { it.isNotBlank() && it != addr.hostAddress }
                if (host != null && host != "localhost") names += host
                if (addr is Inet4Address) {
                    addr.hostAddress?.takeIf { it.isNotBlank() }?.let(names::add)
                }
            }
        }
    }
    if (names.isEmpty()) names += "127.0.0.1"
    return names.toList()
}
