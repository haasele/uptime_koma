package dev.haasele.koma.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.UIKit.UIDevice

actual object Platform {
    actual val kind: PlatformKind = PlatformKind.IOS
    actual val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    /** iOS suspends background execution, so checks only run while the app is active. */
    actual val supportsLongRunningEngine: Boolean = false
    actual val supportsIcmp: Boolean = false

    /** A listening socket only survives while the app is in the foreground. */
    actual val supportsEmbeddedServer: Boolean = false
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
