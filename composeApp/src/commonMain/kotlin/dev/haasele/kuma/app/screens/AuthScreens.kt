package dev.haasele.koma.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.haasele.koma.app.AppSession
import dev.haasele.koma.app.theme.KomaIcons
import dev.haasele.koma.app.ui.InlineMessage
import dev.haasele.koma.app.ui.KomaField

/**
 * Auth is the only full bleed surface in the app: the product name carries the screen, with the
 * form as the single interactive element below it.
 */
@Composable
private fun AuthCanvas(
    headline: String,
    supporting: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.background,
                        scheme.surfaceVariant.copy(alpha = 0.55f),
                        scheme.primary.copy(alpha = 0.18f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KomaIcons.Pulse,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "KUMA",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                        color = scheme.onBackground,
                    )
                    Text(
                        "NATIVE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 9.sp,
                        color = scheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(44.dp))
            Text(headline, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
fun SetupScreen(session: AppSession) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    AuthCanvas(
        headline = "Create your admin account",
        supporting = "Everything stays on this device. The credentials unlock the local monitoring database.",
    ) {
        Column {
            KomaField(username, { username = it }, "Username")
            KomaField(password, { password = it }, "Password", password = true, helper = "At least 8 characters")
            KomaField(confirmation, { confirmation = it }, "Repeat password", password = true)
            InlineMessage(session.error.orEmpty(), error = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { session.createAccount(username, password, confirmation) },
                enabled = !session.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (session.busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Start monitoring")
                }
            }
        }
    }
}

@Composable
fun LoginScreen(session: AppSession) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var showTotp by remember { mutableStateOf(false) }

    AuthCanvas(
        headline = "Welcome back",
        supporting = "Sign in to reach your monitors, status screens and notification channels.",
    ) {
        Column {
            KomaField(username, { username = it }, "Username")
            KomaField(password, { password = it }, "Password", password = true)
            if (showTotp) KomaField(totp, { totp = it }, "Two factor code", numeric = true)
            InlineMessage(session.error.orEmpty(), error = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showTotp = true
                    session.login(username, password, totp)
                },
                enabled = !session.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (session.busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Sign in")
                }
            }
        }
    }
}

@Composable
fun LockedScreen(session: AppSession) {
    // Wait a frame so the Activity is resumed before BiometricPrompt attaches.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        runCatching { session.unlockWithBiometric() }
    }

    AuthCanvas(
        headline = "Unlock",
        supporting = "Your session is still signed in. Confirm with biometrics or enter your password.",
    ) {
        Column {
            InlineMessage(session.error.orEmpty(), error = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { session.unlockWithBiometric() },
                enabled = !session.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (session.busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Unlock with biometrics")
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { session.usePasswordInstead() },
                enabled = !session.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Use password")
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(14.dp))
            Text("Opening the local database", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
