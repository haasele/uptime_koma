package dev.haasele.koma.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.haasele.koma.app.auth.BiometricGate
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.crypto.Totp
import dev.haasele.koma.shared.domain.AppUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

sealed interface Session {
    data object Loading : Session
    data object NeedsSetup : Session
    data object LoggedOut : Session
    /** Persisted login waiting for an optional biometric unlock. */
    data class Locked(val user: AppUser) : Session
    data class Authenticated(val user: AppUser) : Session
}

/**
 * Single user auth, mirroring Uptime Koma's "set it up once" flow. After a successful sign-in the
 * session is remembered locally; Android can optionally gate reopen behind biometrics.
 */
class AppSession(private val core: KomaCore, private val scope: CoroutineScope) {

    var state: Session by mutableStateOf(Session.Loading)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    val biometricAvailable: Boolean get() = BiometricGate.available

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            state = when {
                !core.users.isInitialized() -> Session.NeedsSetup
                !isSessionActive() -> Session.LoggedOut
                else -> {
                    val user = core.users.currentUser()
                    when {
                        user == null -> {
                            clearSession()
                            Session.LoggedOut
                        }
                        isBiometricUnlockEnabled() && BiometricGate.available -> Session.Locked(user)
                        else -> Session.Authenticated(user)
                    }
                }
            }
        }
    }

    fun createAccount(username: String, password: String, confirmation: String) {
        val trimmed = username.trim()
        when {
            trimmed.length < 3 -> return fail("The username needs at least 3 characters")
            password.length < 8 -> return fail("The password needs at least 8 characters")
            password != confirmation -> return fail("The passwords do not match")
        }
        error = null
        busy = true
        scope.launch {
            core.users.createUser(trimmed, password)
            val user = core.users.authenticate(trimmed, password)
            busy = false
            if (user != null) {
                persistSession()
                state = Session.Authenticated(user)
            } else {
                state = Session.LoggedOut
            }
        }
    }

    fun login(username: String, password: String, totpCode: String) {
        error = null
        busy = true
        scope.launch {
            val user = core.users.authenticate(username.trim(), password)
            if (user == null) {
                busy = false
                return@launch fail("Wrong username or password")
            }
            if (user.twoFactorEnabled) {
                val secret = core.users.twoFactorSecret(user.username)
                val seconds = Clock.System.now().epochSeconds
                if (secret == null || !Totp.verify(secret, totpCode.trim(), seconds)) {
                    busy = false
                    return@launch fail("The two factor code is not valid")
                }
            }
            persistSession()
            busy = false
            state = Session.Authenticated(user)
        }
    }

    fun unlockWithBiometric() {
        val locked = state as? Session.Locked ?: return
        error = null
        busy = true
        scope.launch {
            val ok = BiometricGate.authenticate(
                title = "Unlock Uptime Koma",
                subtitle = "Confirm it is you to open ${locked.user.username}'s monitors",
            )
            busy = false
            if (ok) {
                state = Session.Authenticated(locked.user)
            } else {
                error = "Biometric unlock was cancelled or failed"
            }
        }
    }

    fun usePasswordInstead() {
        scope.launch {
            clearSession()
            state = Session.LoggedOut
            error = null
        }
    }

    fun logout() {
        error = null
        scope.launch {
            clearSession()
            state = Session.LoggedOut
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        scope.launch {
            core.settings.putRaw(KEY_BIOMETRIC_UNLOCK, enabled.toString())
        }
    }

    suspend fun isBiometricUnlockEnabled(): Boolean =
        core.settings.getRaw(KEY_BIOMETRIC_UNLOCK)?.toBooleanStrictOrNull() == true

    fun reloadUser() {
        scope.launch {
            core.users.currentUser()?.let { user ->
                state = when (val current = state) {
                    is Session.Locked -> Session.Locked(user)
                    is Session.Authenticated -> Session.Authenticated(user)
                    else -> current
                }
            }
        }
    }

    private suspend fun persistSession() {
        core.settings.putRaw(KEY_SESSION_ACTIVE, "true")
    }

    private suspend fun clearSession() {
        core.settings.putRaw(KEY_SESSION_ACTIVE, "false")
    }

    private suspend fun isSessionActive(): Boolean =
        core.settings.getRaw(KEY_SESSION_ACTIVE)?.toBooleanStrictOrNull() == true

    private fun fail(message: String) {
        error = message
        busy = false
    }

    companion object {
        const val KEY_SESSION_ACTIVE = "sessionActive"
        const val KEY_BIOMETRIC_UNLOCK = "biometricUnlockEnabled"
    }
}
