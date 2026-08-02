package dev.haasele.koma.shared.core

/** Android starts the engine from the foreground service instead of a session hook. */
actual object Autostart {
    actual val supported: Boolean = false
    actual fun isEnabled(): Boolean = false
    actual fun setEnabled(enabled: Boolean): Boolean = false
}
