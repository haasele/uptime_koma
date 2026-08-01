package dev.haasele.koma.shared.core

import java.net.InetSocketAddress
import java.net.ServerSocket

actual fun isTcpPortFree(port: Int): Boolean = runCatching {
    ServerSocket().use { socket ->
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("127.0.0.1", port))
    }
    true
}.getOrDefault(false)
