package dev.haasele.koma.shared.core

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object Platform {
    actual val kind: PlatformKind = PlatformKind.ANDROID
    actual val name: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** Only true while a foreground service holds the engine alive. */
    actual val supportsLongRunningEngine: Boolean = true

    /** Raw ICMP sockets are not available to unprivileged apps; the executor falls back to TCP. */
    actual val supportsIcmp: Boolean = false

    actual val supportsEmbeddedServer: Boolean = true
    actual val localHostNames: List<String>
        get() = buildList {
            runCatching {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return@runCatching
                for (nic in interfaces) {
                    if (!nic.isUp || nic.isLoopback) continue
                    for (addr in nic.inetAddresses) {
                        if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                        addr.hostName?.takeIf { it.isNotBlank() && it != addr.hostAddress }?.let(::add)
                        if (addr is java.net.Inet4Address) addr.hostAddress?.let(::add)
                    }
                }
            }
            if (isEmpty()) add("127.0.0.1")
        }.distinct()
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
