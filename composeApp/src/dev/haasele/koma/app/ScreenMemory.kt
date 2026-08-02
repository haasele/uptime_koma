package dev.haasele.koma.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import dev.haasele.koma.shared.domain.Heartbeat
import dev.haasele.koma.shared.domain.Monitor

/**
 * Survives tab / screen teardown so list screens don't cold-reload the full
 * heartbeat history on every drawer jump.
 */
@Stable
class ScreenMemory {

    /**
     * Per-monitor beat strips. [SnapshotStateMap] so writing one monitor only
     * recomposes that row — not every HeartbeatBar in the list.
     */
    private val serviceBeatsMap: SnapshotStateMap<Long, List<Heartbeat>> = mutableStateMapOf()

    private var serviceTimes: Map<Long, Long> = emptyMap()

    fun serviceBeats(id: Long): List<Heartbeat> = serviceBeatsMap[id].orEmpty()

    fun serviceTime(id: Long): Long? = serviceTimes[id]

    fun hasServiceBeats(id: Long): Boolean = id in serviceBeatsMap

    fun putServiceBeats(id: Long, beats: List<Heartbeat>, timeMs: Long) {
        serviceBeatsMap[id] = beats
        serviceTimes = serviceTimes + (id to timeMs)
    }

    fun pruneServiceBeats(validIds: Set<Long>) {
        val stale = serviceBeatsMap.keys.filter { it !in validIds }
        if (stale.isEmpty()) return
        stale.forEach { serviceBeatsMap.remove(it) }
        serviceTimes = serviceTimes.filterKeys { it in validIds }
    }

    /**
     * Last known monitor list / latest beats for the dashboard headline.
     * Used as [androidx.compose.runtime.collectAsState] initial so remounts
     * don't flash "Ready when you are" while flows re-emit.
     */
    var lastMonitors by mutableStateOf<List<Monitor>?>(null)
    var lastLatest by mutableStateOf<Map<Long, Heartbeat>>(emptyMap())

    /** Opaque dashboard insights snapshot (typed by DashboardScreen). */
    var dashboardKey: String = ""
        private set
    var dashboardSlot: Any? = null
        private set

    fun saveDashboard(monitorKey: String, insights: Any) {
        dashboardKey = monitorKey
        dashboardSlot = insights
    }

    fun clearDashboard() {
        dashboardKey = ""
        dashboardSlot = null
    }
}

/** How many recent beats the service list strips need (bar capacity ≤ this). */
const val SERVICE_BEAT_LIMIT = 200
