package dev.haasele.koma.app.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.BindException

/**
 * Android kills background work quickly, so the engine only stays alive behind an ongoing
 * notification. Users who do not want it can stop the service from the notification shade.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, error ->
            if (error is BindException || error.cause is BindException) {
                Log.w(TAG, "Embedded server port busy; continuing without HTTP endpoints", error)
            } else {
                Log.e(TAG, "MonitorService coroutine failed", error)
            }
        },
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            runCatching { komaCore.start() }
                .onFailure { Log.e(TAG, "Failed to start KomaCore", it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Monitoring engine", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the checks running while the app is in the background"
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Uptime Koma")
        .setContentText("Monitoring is running")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    private companion object {
        const val TAG = "KomaMonitor"
        const val CHANNEL_ID = "koma_engine"
        const val NOTIFICATION_ID = 4711
    }
}
