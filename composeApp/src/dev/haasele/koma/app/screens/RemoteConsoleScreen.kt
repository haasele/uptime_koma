package dev.haasele.koma.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.remote.RemoteConnectionState
import dev.haasele.koma.app.remote.RemoteSession
import dev.haasele.koma.app.ui.EmptyState
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaField
import dev.haasele.koma.app.ui.SectionTitle
import dev.haasele.koma.app.ui.StatusDot
import dev.haasele.koma.app.ui.asMillis
import dev.haasele.koma.shared.core.relativeToNow
import dev.haasele.koma.shared.domain.MonitorStatus

/**
 * The mobile half of phase three: this device shows and controls the monitors of a desktop
 * instance instead of running its own engine.
 */
@Composable
fun RemoteConsoleScreen(session: RemoteSession) {
    LaunchedEffect(Unit) { session.restore() }

    val connected = session.connection == RemoteConnectionState.CONNECTED

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column {
                Text(
                    "Remote instance",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    text = if (connected) {
                        "Connected to ${session.serverName}, engine ${if (session.engineRunning) "running" else "stopped"}"
                    } else {
                        "Point this app at a desktop that has remote access enabled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column {
                KomaField(
                    session.url,
                    { session.url = it },
                    "Server address",
                    placeholder = "http://192.168.1.10:3001",
                    enabled = !connected,
                )
                KomaField(
                    session.token,
                    { session.token = it },
                    "Access token",
                    helper = "Shown in the desktop settings under remote access",
                    password = true,
                    enabled = !connected,
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (connected) session.disconnect() else session.connect() },
                        enabled = connected || (session.url.isNotBlank() && session.token.isNotBlank()),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            when {
                                connected -> "Disconnect"
                                session.connection == RemoteConnectionState.CONNECTING -> "Connecting…"
                                else -> "Connect"
                            },
                        )
                    }
                    if (connected) {
                        OutlinedButton(
                            onClick = { session.controlEngine(!session.engineRunning) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(if (session.engineRunning) "Stop engine" else "Start engine")
                        }
                    }
                }
            }
        }

        if (session.message.isNotBlank()) {
            item { InlineMessage(session.message, error = !connected) }
        }

        if (connected) {
            item {
                SectionTitle("Monitors", trailing = {
                    AssistChip(onClick = { session.refresh() }, label = { Text("Refresh") })
                })
            }

            if (session.monitors.isEmpty()) {
                item {
                    EmptyState(
                        title = "No monitors yet",
                        message = "The remote instance has not been set up with any checks.",
                    )
                }
            }

            items(session.monitors, key = { it.id }) { monitor ->
                val beat = session.beats[monitor.id]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(beat?.status ?: MonitorStatus.PENDING, monitor.active)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(monitor.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = beat?.message?.takeIf { it.isNotBlank() }
                                ?: monitor.displayTarget.ifBlank { monitor.type.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(beat?.pingMs.asMillis(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = beat?.timeMs?.relativeToNow().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    AssistChip(
                        onClick = { session.setActive(monitor.id, !monitor.active) },
                        label = { Text(if (monitor.active) "Pause" else "Resume") },
                    )
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}
