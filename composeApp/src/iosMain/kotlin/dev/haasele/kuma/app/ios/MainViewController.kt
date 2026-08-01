package dev.haasele.koma.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import dev.haasele.koma.app.KomaApp
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.data.IosDatabaseDriverFactory
import dev.haasele.koma.shared.notify.IosNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

private val core: KomaCore by lazy {
    KomaCore.create(
        driverFactory = IosDatabaseDriverFactory(),
        parentContext = Dispatchers.Default,
        localNotifier = IosNotifier(),
    )
}

/** Entry point the Xcode project instantiates from SwiftUI. */
fun MainViewController(): UIViewController {
    CoroutineScope(Dispatchers.Main + SupervisorJob()).launch { core.start() }
    return ComposeUIViewController { KomaApp(core) }
}
