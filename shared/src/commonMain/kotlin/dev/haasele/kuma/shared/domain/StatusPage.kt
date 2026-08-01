package dev.haasele.koma.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class StatusPage(
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    val icon: String? = null,
    val theme: String = "auto",
    val published: Boolean = true,
    val showTags: Boolean = false,
    val showUptimePercentage: Boolean = true,
    val showCertificateExpiry: Boolean = false,
    val footerText: String? = null,
    val accentColor: String? = null,
    val hasPassword: Boolean = false,
    val createdAt: Long = 0,
    val groups: List<StatusPageGroup> = emptyList(),
)

@Serializable
data class StatusPageGroup(
    val id: Long = 0,
    val name: String = "",
    val weight: Int = 0,
    val monitorIds: List<Long> = emptyList(),
)

@Serializable
enum class IncidentStyle { INFO, WARNING, DANGER, PRIMARY, LIGHT, DARK }

@Serializable
data class Incident(
    val id: Long = 0,
    val statusPageId: Long = 0,
    val title: String = "",
    val content: String = "",
    val style: IncidentStyle = IncidentStyle.WARNING,
    val pinned: Boolean = true,
    val createdAt: Long = 0,
    val lastUpdatedAt: Long = 0,
)

/** Read model the status screen renders; mirrors what the web status page would publish. */
@Serializable
data class StatusPageView(
    val page: StatusPage,
    val pinnedIncident: Incident?,
    val groups: List<StatusPageGroupView>,
    val activeMaintenances: List<Maintenance>,
    val overall: MonitorStatus,
)

@Serializable
data class StatusPageGroupView(
    val group: StatusPageGroup,
    val monitors: List<StatusPageMonitorView>,
)

@Serializable
data class StatusPageMonitorView(
    val monitor: Monitor,
    val stats: UptimeStats,
    val recentBeats: List<Heartbeat>,
)
