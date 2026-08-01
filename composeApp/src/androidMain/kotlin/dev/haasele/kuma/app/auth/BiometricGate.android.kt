package dev.haasele.koma.app.auth

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AndroidBiometricHost {
    @Volatile
    var activity: FragmentActivity? = null
}

actual object BiometricGate {
    actual val available: Boolean
        get() {
            val activity = AndroidBiometricHost.activity ?: return false
            val manager = BiometricManager.from(activity)
            val result = manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

    actual suspend fun authenticate(title: String, subtitle: String): Boolean {
        val activity = AndroidBiometricHost.activity ?: return false
        if (!available) return false

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        // Keep the prompt open for another attempt; do not resume yet.
                    }
                },
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()

            Handler(Looper.getMainLooper()).post {
                runCatching { prompt.authenticate(info) }
                    .onFailure { if (continuation.isActive) continuation.resume(false) }
            }

            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { prompt.cancelAuthentication() }
            }
        }
    }
}
