package dev.haasele.koma.shared.core

/**
 * Launching with the session is what turns the desktop build into an always on monitor. Mobile
 * platforms do not offer an equivalent, so they report it as unsupported.
 */
expect object Autostart {
    val supported: Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean): Boolean
}
