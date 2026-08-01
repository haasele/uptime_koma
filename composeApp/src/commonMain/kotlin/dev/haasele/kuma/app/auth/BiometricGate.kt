package dev.haasele.koma.app.auth

/**
 * Optional device unlock after a persisted login. Desktop and iOS report unavailable; Android
 * wires BiometricPrompt when the hardware supports it.
 */
expect object BiometricGate {
    val available: Boolean
    suspend fun authenticate(title: String, subtitle: String): Boolean
}
