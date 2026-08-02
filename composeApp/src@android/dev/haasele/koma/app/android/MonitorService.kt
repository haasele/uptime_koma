package dev.haasele.koma.app.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.haasele.koma.shared.notify.AndroidNotificationChannels
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.BindException

/**
 * Android kills background work quickly, so the engine only stays alive behind an ongoing
 * notification on the [AndroidNotificationChannels.PERSISTENT_ID] channel. Users can mute
 * that channel (or the separate alerts channel) independently in system settings.
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
        AndroidNotificationChannels.ensure(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(
        this,
        AndroidNotificationChannels.PERSISTENT_ID,
    )
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Uptime Koma")
        .setContentText("Monitoring is running")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setSilent(true)
        .build()

    private companion object {
        const val TAG = "KomaMonitor"
        const val NOTIFICATION_ID = 4711
    }
}
