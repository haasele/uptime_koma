package dev.haasele.koma.app.auth

actual object BiometricGate {
    actual val available: Boolean = false
    actual suspend fun authenticate(title: String, subtitle: String): Boolean = false
}
