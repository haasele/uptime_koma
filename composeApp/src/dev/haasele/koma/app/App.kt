package dev.haasele.koma.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.nav.Navigator
import dev.haasele.koma.app.nav.Screen
import dev.haasele.koma.app.remote.RemoteSession
import dev.haasele.koma.app.screens.DashboardScreen
import dev.haasele.koma.app.screens.LoadingScreen
import dev.haasele.koma.app.screens.LockedScreen
import dev.haasele.koma.app.screens.LoginScreen
import dev.haasele.koma.app.screens.MaintenanceScreen
import dev.haasele.koma.app.screens.MonitorDetailScreen
import dev.haasele.koma.app.screens.MonitorEditorScreen
import dev.haasele.koma.app.screens.NotificationsScreen
import dev.haasele.koma.app.screens.RemoteConsoleScreen
import dev.haasele.koma.app.screens.ServiceScreen
import dev.haasele.koma.app.screens.SettingsScreen
import dev.haasele.koma.app.screens.SetupScreen
import dev.haasele.koma.app.screens.StatusPageEditorScreen
import dev.haasele.koma.app.screens.StatusPageListScreen
import dev.haasele.koma.app.screens.StatusPageViewerScreen
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.theme.KomaMotion
import dev.haasele.koma.app.theme.KomaTheme
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.core.Platform
import dev.haasele.koma.shared.core.PlatformKind
import dev.haasele.koma.shared.domain.AppSettings
import kotlinx.coroutines.launch

/** Permanent side rail only when there is room left for the main pane after the drawer. */
private val PermanentDrawerBreakpoint = 960.dp

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KomaApp(core: KomaCore) {
    val settings by core.settings.observe().collectAsState(AppSettings())
    val scope = rememberCoroutineScope()
    val session = remember { AppSession(core, scope) }

    KomaTheme(themePreference = settings.theme, accentHex = settings.accentColor) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = session.state,
                contentKey = { state ->
                    when (state) {
                        Session.Loading -> "loading"
                        Session.NeedsSetup -> "setup"
                        Session.LoggedOut -> "loggedOut"
                        is Session.Locked -> "locked"
                        is Session.Authenticated -> "auth"
                    }
                },
                transitionSpec = { KomaMotion.contentCrossfade() },
                label = "auth-shell",
            ) { state ->
                when (state) {
                    Session.Loading -> LoadingScreen()
                    Session.NeedsSetup -> SetupScreen(session)
                    Session.LoggedOut -> LoginScreen(session)
                    is Session.Locked -> LockedScreen(session)
                    is Session.Authenticated -> MainShell(core, session)
                }
            }
        }
    }
}

private data class TabEntry(val screen: Screen.Tab, val icon: ImageVector)

private val tabEntries = listOf(
    TabEntry(Screen.Dashboard, Icons.Default.Home),
    TabEntry(Screen.Services, KomaIcons.Pulse),
    TabEntry(Screen.StatusScreens, KomaIcons.Layers),
    TabEntry(Screen.Notifications, Icons.Default.Notifications),
    TabEntry(Screen.Maintenance, KomaIcons.Wrench),
    TabEntry(Screen.Settings, Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun MainShell(core: KomaCore, session: AppSession) {
    val navigator = remember { Navigator() }
    val scope = rememberCoroutineScope()
    val remoteSession = remember { RemoteSession(core, scope) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    DisposableEffect(remoteSession) { onDispose { remoteSession.close() } }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && navigator.canGoBack) {
        navigator.pop()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isDesktop = Platform.kind == PlatformKind.DESKTOP
        // Desktop always uses the permanent drawer; hide the TopAppBar so content is full height.
        val wide = isDesktop || maxWidth >= PermanentDrawerBreakpoint
        val showTopBar = !isDesktop
        val drawerWidth = when {
            maxWidth >= 1280.dp -> 280.dp
            maxWidth >= PermanentDrawerBreakpoint || isDesktop -> 240.dp
            else -> 300.dp
        }

        val content: @Composable () -> Unit = {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    if (showTopBar) {
                    TopAppBar(
                        title = {
                            when {
                                navigator.current == Screen.RemoteConsole -> Text("Remote")
                                navigator.canGoBack -> Text(
                                    navigator.currentTab.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Wide tablets: brand + tab labels live in the permanent left drawer.
                                wide -> Unit
                                else -> Brand()
                            }
                        },
                        navigationIcon = {
                            if (navigator.canGoBack) {
                                IconButton(onClick = { navigator.pop() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else if (!wide) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                    )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ScreenContent(core, session, navigator, remoteSession)
                }
            }
        }

        val drawerContent: @Composable () -> Unit = {
            DrawerBody(
                selected = navigator.currentTab,
                onSelect = { tab ->
                    navigator.selectTab(tab)
                    scope.launch { drawerState.close() }
                },
            )
        }

        if (wide) {
            PermanentNavigationDrawer(
                modifier = Modifier.fillMaxSize(),
                drawerContent = {
                    PermanentDrawerSheet(
                        Modifier
                            .width(drawerWidth)
                            .widthIn(max = drawerWidth)
                            .fillMaxHeight(),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    ) { drawerContent() }
                },
            ) { content() }
        } else {
            ModalNavigationDrawer(
                modifier = Modifier.fillMaxSize(),
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        Modifier.width(drawerWidth).fillMaxHeight(),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    ) { drawerContent() }
                },
            ) { content() }
        }
    }
}

@Composable
private fun DrawerBody(selected: Screen.Tab, onSelect: (Screen.Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Brand()
        }
        Spacer(Modifier.height(16.dp))
        tabEntries.forEach { entry ->
            NavigationDrawerItem(
                selected = selected == entry.screen,
                onClick = { onSelect(entry.screen) },
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(entry.screen.title) },
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScreenContent(
    core: KomaCore,
    session: AppSession,
    navigator: Navigator,
    remoteSession: RemoteSession,
) {
    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = { KomaMotion.screenTransition(navigator.lastAction) },
        label = "screen-content",
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        when (screen) {
            Screen.Dashboard -> DashboardScreen(
                core = core,
                onOpenMonitor = { navigator.push(Screen.MonitorDetail(it)) },
                onOpenServices = { navigator.selectTab(Screen.Services) },
                onOpenStatus = { navigator.selectTab(Screen.StatusScreens) },
            )

            Screen.Services -> ServiceScreen(
                core = core,
                onOpenMonitor = { navigator.push(Screen.MonitorDetail(it)) },
                onCreateMonitor = { navigator.push(Screen.MonitorEditor(null)) },
            )

            Screen.StatusScreens -> StatusPageListScreen(
                core = core,
                onOpen = { navigator.push(Screen.StatusPageViewer(it)) },
                onEdit = { navigator.push(Screen.StatusPageEditor(it)) },
            )

            Screen.Notifications -> NotificationsScreen(core)

            Screen.Maintenance -> MaintenanceScreen(core)

            Screen.Settings -> SettingsScreen(
                core = core,
                session = session,
                onOpenRemoteConsole = { navigator.push(Screen.RemoteConsole) },
            )

            Screen.RemoteConsole -> RemoteConsoleScreen(remoteSession)

            is Screen.MonitorDetail -> MonitorDetailScreen(
                core = core,
                monitorId = screen.monitorId,
                onEdit = { navigator.push(Screen.MonitorEditor(it)) },
                onBack = { navigator.pop() },
            )

            is Screen.MonitorEditor -> MonitorEditorScreen(
                core = core,
                monitorId = screen.monitorId,
                parentId = screen.parentId,
                onSaved = { id ->
                    navigator.pop()
                    if (screen.monitorId == null) navigator.push(Screen.MonitorDetail(id))
                },
                onCancel = { navigator.pop() },
            )

            is Screen.StatusPageEditor -> StatusPageEditorScreen(
                core = core,
                pageId = screen.pageId,
                onSaved = { id ->
                    navigator.pop()
                    if (screen.pageId == null) navigator.push(Screen.StatusPageViewer(id))
                },
                onCancel = { navigator.pop() },
            )

            is Screen.StatusPageViewer -> StatusPageViewerScreen(
                core = core,
                pageId = screen.pageId,
                onEdit = { navigator.push(Screen.StatusPageEditor(it)) },
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                KomaIcons.Pulse,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "UPTIME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "KOMA",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
        )
    }
}
