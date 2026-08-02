package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.data.NotificationRepository
import dev.haasele.koma.shared.domain.CertificateInfo
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.NotificationChannel
import dev.haasele.koma.shared.domain.NotificationEvent
import dev.haasele.koma.shared.net.HttpClientProvider
import dev.haasele.koma.shared.net.HttpClientSpec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NotificationFailure(val channelName: String, val reason: String)

/**
 * Fans a status change out to every channel attached to the monitor. A failing channel is
 * reported but never aborts delivery to the remaining ones.
 */
class NotificationDispatcher(
    private val notifications: NotificationRepository,
    private val httpClients: HttpClientProvider,
) {
    private val _failures = MutableSharedFlow<NotificationFailure>(extraBufferCapacity = 32)
    val failures: SharedFlow<NotificationFailure> = _failures.asSharedFlow()

    private val http get() = httpClients.get(HttpClientSpec(timeoutMs = 20_000))

    suspend fun notifyStatusChange(monitor: Monitor, heartbeat: Heartbeat) {
        val channels = notifications.getForMonitor(monitor.id)
        if (channels.isEmpty()) return

        val event = NotificationEvent(
            monitor = monitor,
            heartbeat = heartbeat,
            title = monitor.name,
            message = buildString {
                append(heartbeat.message)
                append("\nTime: ").append(heartbeat.timeMs.formatDateTime())
            },
        )
        channels.forEach { channel -> deliver(channel, event) }
    }

    suspend fun notifyCertificateExpiry(monitor: Monitor, certificate: CertificateInfo, thresholdDays: Int) {
        val channels = notifications.getForMonitor(monitor.id)
        if (channels.isEmpty()) return

        val event = NotificationEvent(
            monitor = monitor,
            heartbeat = null,
            title = "Certificate expiring",
            message = "The TLS certificate for ${monitor.name} expires in ${certificate.daysRemaining} days " +
                "(threshold $thresholdDays days).",
        )
        channels.forEach { channel -> deliver(channel, event) }
    }

    suspend fun test(channel: NotificationChannel): Result<Unit> {
        val event = NotificationEvent(
            monitor = null,
            heartbeat = null,
            title = "Uptime Koma",
            message = "This is a test notification from Uptime Koma.",
            isTest = true,
        )
        return runCatching { sendOrThrow(channel, event) }
    }

    private suspend fun deliver(channel: NotificationChannel, event: NotificationEvent) {
        if (!channel.active) return
        runCatching { sendOrThrow(channel, event) }.onFailure { error ->
            _failures.emit(NotificationFailure(channel.name, error.message ?: "Delivery failed"))
        }
    }

    private suspend fun sendOrThrow(channel: NotificationChannel, event: NotificationEvent) {
        val provider = NotificationRegistry.byId(channel.provider)
            ?: throw NotificationException("Unknown notification provider \"${channel.provider}\"")
        val missing = provider.validate(channel.config)
        if (missing.isNotEmpty()) throw NotificationException(missing.joinToString())
        provider.send(channel.config, event, http)
    }
}
