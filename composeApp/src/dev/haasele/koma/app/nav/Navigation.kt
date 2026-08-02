package dev.haasele.koma.app.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

/** Last navigation cause — drives enter/exit direction in [AnimatedContent]. */
enum class NavAction {
    Push,
    Pop,
    Replace,
    /** Drawer/tab jump toward a lower entry (e.g. Dashboard → Settings). */
    TabDown,
    /** Drawer/tab jump toward a higher entry (e.g. Settings → Dashboard). */
    TabUp,
}

@Stable
class Navigator(start: Screen.Tab = Screen.Dashboard) {

    private val stack = mutableStateListOf<Screen>(start)

    var lastAction by mutableStateOf(NavAction.TabDown)
        private set

    val current: Screen get() = stack.last()

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
