package dev.haasele.koma.app.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

sealed interface Screen {

    /** Root destinations reachable from the navigation drawer. */
    sealed interface Tab : Screen {
        val title: String
    }

    data object Dashboard : Tab {
        override val title = "Dashboard"
    }

    data object Services : Tab {
        override val title = "Services"
    }

    data object StatusScreens : Tab {
        override val title = "Status"
    }

    data object Notifications : Tab {
        override val title = "Notifications"
    }

    data object Maintenance : Tab {
        override val title = "Maintenance"
    }

    data object Settings : Tab {
        override val title = "Settings"
    }

    data object RemoteConsole : Screen

    data class MonitorDetail(val monitorId: Long) : Screen
    data class MonitorEditor(val monitorId: Long?, val parentId: Long? = null) : Screen
    data class StatusPageEditor(val pageId: Long?) : Screen
    data class StatusPageViewer(val pageId: Long) : Screen

    companion object {
        val tabs = listOf(Dashboard, Services, StatusScreens, Notifications, Maintenance, Settings)
    }
}

/** Last navigation cause — drives enter/exit direction in AnimatedContent. */
enum class NavAction {
    Push,
    Pop,
    Replace,
    /** Drawer/tab jump toward a lower entry (e.g. Dashboard → Settings). */
    TabDown,
    /** Drawer/tab jump toward a higher entry (e.g. Settings → Dashboard). */
    TabUp,
}

/**
 * Bundles screen + action so AnimatedContent's `transitionSpec` reads the action
 * that caused *this* change (not a stale external State read).
 */
data class NavFrame(val screen: Screen, val action: NavAction)

/** False while a screen enter/exit transition is running — skip heavy enter work. */
val LocalNavSettled = staticCompositionLocalOf { true }

/** Prefer tab order geometry over the recorded action when both ends are tabs. */
fun resolveNavAction(from: Screen, to: Screen, recorded: NavAction): NavAction {
    if (from is Screen.Tab && to is Screen.Tab) {
        val fi = Screen.tabs.indexOf(from)
        val ti = Screen.tabs.indexOf(to)
        return when {
            ti > fi -> NavAction.TabDown
            ti < fi -> NavAction.TabUp
            else -> recorded
        }
    }
    return recorded
}

fun screenContentKey(screen: Screen): String = when (screen) {
    is Screen.Tab -> "tab-${screen.title}"
    is Screen.MonitorDetail -> "monitor-${screen.monitorId}"
    is Screen.MonitorEditor -> "editor-${screen.monitorId}-${screen.parentId}"
    is Screen.StatusPageViewer -> "status-${screen.pageId}"
    is Screen.StatusPageEditor -> "status-edit-${screen.pageId}"
    Screen.RemoteConsole -> "remote"
}

@Stable
class Navigator(start: Screen.Tab = Screen.Dashboard) {

    private val stack = mutableStateListOf<Screen>(start)

    var lastAction by mutableStateOf(NavAction.TabDown)
        private set

    val current: Screen get() = stack.last()

    val frame: NavFrame get() = NavFrame(current, lastAction)

    val currentTab: Screen.Tab get() = stack.first() as Screen.Tab

    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        lastAction = NavAction.Push
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        lastAction = NavAction.Pop
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the current detail screen, used after saving a newly created entity. */
    fun replaceTop(screen: Screen) {
        lastAction = NavAction.Replace
        stack[stack.lastIndex] = screen
    }

    fun selectTab(tab: Screen.Tab) {
        val from = Screen.tabs.indexOf(currentTab)
        val to = Screen.tabs.indexOf(tab)
        lastAction = when {
            to > from -> NavAction.TabDown
            to < from -> NavAction.TabUp
            else -> NavAction.TabDown
        }
        stack.clear()
        stack.add(tab)
    }
}
