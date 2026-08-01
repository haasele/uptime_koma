package dev.haasele.koma.shared.core

/** iOS gives an app no way to launch itself, so this stays off. */
actual object Autostart {
    actual val supported: Boolean = false
    actual fun isEnabled(): Boolean = false
    actual fun setEnabled(enabled: Boolean): Boolean = false
}
