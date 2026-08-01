package dev.haasele.koma.app.remote

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.remote.RemoteClient
import dev.haasele.koma.shared.remote.RemoteCommand
import dev.haasele.koma.shared.remote.RemoteEvent
import dev.haasele.koma.shared.remote.RemotePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class RemoteConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Drives a desktop engine from another device. The screen only ever reads this holder, so the
 * socket survives navigation between the console and the rest of the app.
 */
@Stable
class RemoteSession(private val core: KomaCore, private val scope: CoroutineScope) {

    private val client = RemoteClient(scope)
    private var eventJob: Job? = null

    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var connection by mutableStateOf(RemoteConnectionState.DISCONNECTED)
        private set
    var serverName by mutableStateOf("")
        private set
    var monitors by mutableStateOf<List<Monitor>>(emptyList())
        private set
    var beats by mutableStateOf<Map<Long, Heartbeat>>(emptyMap())
        private set
    var engineRunning by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set

    suspend fun restore() {
        url = core.settings.getRaw(KEY_URL).orEmpty()
        token = core.settings.getRaw(KEY_TOKEN).orEmpty()
    }

    fun connect() {
        if (connection == RemoteConnectionState.CONNECTING) return
        connection = RemoteConnectionState.CONNECTING
        message = ""
        scope.launch {
            try {
                serverName = client.connect(url.trim(), token.trim())
                connection = RemoteConnectionState.CONNECTED
                core.settings.putRaw(KEY_URL, url.trim())
                core.settings.putRaw(KEY_TOKEN, token.trim())
                observeEvents()
                refresh()
            } catch (error: Throwable) {
                connection = RemoteConnectionState.DISCONNECTED
                message = error.message ?: "Could not connect"
            }
        }
    }

    fun disconnect() {
        scope.launch {
            eventJob?.cancel()
            eventJob = null
            client.disconnect()
            connection = RemoteConnectionState.DISCONNECTED
            monitors = emptyList()
            beats = emptyMap()
        }
    }

    fun refresh() {
        scope.launch { loadMonitors() }
    }

    fun setActive(monitorId: Long, active: Boolean) {
        scope.launch {
            runRemote { client.request(RemoteCommand.SetMonitorActive(monitorId, active)) }
            loadMonitors()
        }
    }

    fun controlEngine(start: Boolean) {
        scope.launch {
            runRemote { client.request(RemoteCommand.ControlEngine(start)) }
            engineRunning = start
        }
    }

    fun testCheck(monitor: Monitor) {
        scope.launch {
            val payload = runRemote { client.request(RemoteCommand.TestCheck(monitor)) }
            message = when (payload) {
                is RemotePayload.CheckOutcome -> "${monitor.name}: ${payload.message}"
                is RemotePayload.Error -> payload.message
                else -> ""
            }
        }
    }

    private suspend fun loadMonitors() {
        val payload = runRemote { client.request(RemoteCommand.ListMonitors) } ?: return
        if (payload is RemotePayload.Monitors) {
            monitors = payload.monitors
            beats = payload.latestBeats
        }
    }

    private fun observeEvents() {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events.collect { event ->
                when (event) {
                    is RemoteEvent.Beat -> beats = beats + (event.monitorId to event.heartbeat)
                    is RemoteEvent.EngineRunning -> engineRunning = event.running
                    RemoteEvent.MonitorsChanged -> loadMonitors()
                }
            }
        }
    }

    private suspend fun <T> runRemote(block: suspend () -> T): T? = try {
        block()
    } catch (error: Throwable) {
        message = error.message ?: "The remote instance did not answer"
        connection = RemoteConnectionState.DISCONNECTED
        null
    }

    fun close() {
        eventJob?.cancel()
        client.close()
    }

    companion object {
        const val KEY_URL = "remoteClientUrl"
        const val KEY_TOKEN = "remoteClientToken"
    }
}
