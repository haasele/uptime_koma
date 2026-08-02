package dev.haasele.koma.shared.net

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlin.coroutines.coroutineContext

/** Thin wrapper so protocol checks do not each repeat selector and channel bookkeeping. */
class TcpConnection internal constructor(
    private val selector: SelectorManager,
    private val socket: Socket,
    val read: ByteReadChannel,
    val write: ByteWriteChannel,
) {
    suspend fun send(bytes: ByteArray) {
        write.writeFully(bytes)
        write.flush()
    }

    suspend fun receive(maxBytes: Int = 4096): ByteArray {
        val buffer = ByteArray(maxBytes)
        val count = read.readAvailable(buffer, 0, maxBytes)
        return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }

    suspend fun readLine(): String {
        val builder = StringBuilder()
        while (true) {
            val byte = runCatching { read.readByte() }.getOrNull() ?: break
            if (byte == '\n'.code.toByte()) break
            if (byte != '\r'.code.toByte()) builder.append(byte.toInt().toChar())
        }
        return builder.toString()
    }

    fun close() {
        runCatching { socket.close() }
        runCatching { selector.close() }
    }
}

suspend fun openTcp(host: String, port: Int, timeoutMs: Long, useTls: Boolean = false): TcpConnection =
    withTimeout(timeoutMs) {
        val selector = SelectorManager(Dispatchers.Default)
        val rawSocket = runCatching { aSocket(selector).tcp().connect(InetSocketAddress(host, port)) }
            .getOrElse { error ->
                selector.close()
                throw error
            }
        val socket = if (useTls) {
            runCatching { rawSocket.tls(coroutineContext) }.getOrElse { error ->
                rawSocket.close()
                selector.close()
                throw error
            }
        } else {
            rawSocket
        }
        TcpConnection(selector, socket, socket.openReadChannel(), socket.openWriteChannel(autoFlush = false))
    }

/** Connects to a unix domain socket, used to talk to a local Docker daemon. */
suspend fun openUnixSocket(path: String, timeoutMs: Long): TcpConnection = withTimeout(timeoutMs) {
    val selector = SelectorManager(Dispatchers.Default)
    val socket = runCatching {
        aSocket(selector).tcp().connect(io.ktor.network.sockets.UnixSocketAddress(path))
    }.getOrElse { error ->
        selector.close()
        throw error
    }
    TcpConnection(selector, socket, socket.openReadChannel(), socket.openWriteChannel(autoFlush = false))
}

/** Single request/response exchange over UDP, used by the DNS and Steam query protocols. */
suspend fun udpExchange(host: String, port: Int, payload: ByteArray, timeoutMs: Long): ByteArray {
    val selector = SelectorManager(Dispatchers.Default)
    try {
        val address = InetSocketAddress(host, port)
        val socket = aSocket(selector).udp().connect(address)
        try {
            return withTimeout(timeoutMs) {
                socket.send(io.ktor.network.sockets.Datagram(kotlinx.io.Buffer().apply { write(payload) }, address))
                socket.receive().packet.readByteArray()
            }
        } finally {
            socket.close()
        }
    } finally {
        selector.close()
    }
}

suspend inline fun <T> useTcp(
    host: String,
    port: Int,
    timeoutMs: Long,
    useTls: Boolean = false,
    block: (TcpConnection) -> T,
): T {
    val connection = openTcp(host, port, timeoutMs, useTls)
    return try {
        block(connection)
    } finally {
        connection.close()
    }
}
