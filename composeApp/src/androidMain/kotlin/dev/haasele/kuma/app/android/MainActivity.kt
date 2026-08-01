package dev.haasele.koma.app.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.haasele.koma.app.KomaApp
import dev.haasele.koma.app.auth.AndroidBiometricHost
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidBiometricHost.activity = this

        // Idempotent — MonitorService may call the same entry; only the first bind wins.
        lifecycleScope.launch {
            runCatching { komaCore.start() }
                .onFailure { Log.e(TAG, "Failed to start KomaCore", it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        }.onFailure { Log.e(TAG, "Could not start MonitorService", it) }

        setContent { KomaApp(komaCore) }
    }

    override fun onDestroy() {
        if (AndroidBiometricHost.activity === this) AndroidBiometricHost.activity = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KomaMain"
    }
}
