package dev.haasele.koma.shared.core

import kotlinx.coroutines.CoroutineDispatcher

enum class PlatformKind { DESKTOP, ANDROID, IOS }

expect object Platform {
    val kind: PlatformKind
    val name: String

    /** Whether the platform lets the engine keep polling while the app is not in the foreground. */
    val supportsLongRunningEngine: Boolean

    /** Raw ICMP usually needs elevated rights; the ping executor falls back when this is false. */
    val supportsIcmp: Boolean

    /** Binding a listening socket for push monitors and the metrics endpoint. */
    val supportsEmbeddedServer: Boolean
}

expect val ioDispatcher: CoroutineDispatcher
