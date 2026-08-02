package dev.haasele.koma.shared.remote

import dev.haasele.koma.shared.core.KomaJson
import dev.haasele.koma.shared.crypto.randomToken
import dev.haasele.koma.shared.net.HttpClientProvider
import dev.haasele.koma.shared.net.HttpClientSpec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class RemoteException(message: String) : Exception(message)

/**
 * Client half of the remote protocol: a mobile device drives the engine running on a desktop.
 * Requests are correlated by id so several calls can be in flight on one socket.
 */
class RemoteClient(private val scope: CoroutineScope) {

    private val httpClients = HttpClientProvider()
    private val pending = mutableMapOf<String, CompletableDeferred<RemotePayload>>()
    private val pendingLock = Mutex()

    private var session: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null

    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RemoteEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    suspend fun connect(baseUrl: String, token: String): String {
        disconnect()

        val endpoint = baseUrl.trimEnd('/')
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .let { if (it.startsWith("ws")) it else "ws://$it" } + "/api/remote"

        val client = httpClients.get(HttpClientSpec(timeoutMs = 15_000))
        val opened = client.webSocketSession(endpoint)
        session = opened

        readerJob = scope.launch {
            try {
                for (frame in opened.incoming) {
                    if (frame !is Frame.Text) continue
                    when (val decoded = KomaJson.decodeFromString(RemoteFrame.serializer(), frame.readText())) {
                        is RemoteFrame.Response -> pendingLock.withLock { pending.remove(decoded.id) }
                            ?.complete(decoded.payload)
                        is RemoteFrame.Event -> _events.emit(decoded.event)
                        is RemoteFrame.Request -> Unit
                    }
                }
            } finally {
                _connected.value = false
            }
        }

        return when (val payload = request(RemoteCommand.Authenticate(token))) {
            is RemotePayload.Authenticated -> {
                _connected.value = true
                payload.serverName
            }
            is RemotePayload.Error -> {
                disconnect()
                throw RemoteException(payload.message)
            }
            else -> {
                disconnect()
                throw RemoteException("Unexpected response to authentication")
            }
        }
    }

    suspend fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        runCatching { session?.close() }
        session = null
        _connected.value = false
        pendingLock.withLock {
            pending.values.forEach { it.complete(RemotePayload.Error("Connection closed")) }
            pending.clear()
        }
    }

    suspend fun request(command: RemoteCommand, timeoutMs: Long = 20_000): RemotePayload {
        val active = session ?: throw RemoteException("Not connected")
        val id = randomToken(12)
        val deferred = CompletableDeferred<RemotePayload>()
        pendingLock.withLock { pending[id] = deferred }

        active.send(
            Frame.Text(KomaJson.encodeToString(RemoteFrame.serializer(), RemoteFrame.Request(id, command))),
        )

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (error: Throwable) {
            pendingLock.withLock { pending.remove(id) }
            throw RemoteException(error.message ?: "Request timed out")
        }
    }

    suspend inline fun <reified T : RemotePayload> requireResponse(command: RemoteCommand): T =
        when (val payload = request(command)) {
            is T -> payload
            is RemotePayload.Error -> throw RemoteException(payload.message)
            else -> throw RemoteException("Unexpected response ${payload::class.simpleName}")
        }

    fun close() {
        readerJob?.cancel()
        httpClients.close()
    }
}
