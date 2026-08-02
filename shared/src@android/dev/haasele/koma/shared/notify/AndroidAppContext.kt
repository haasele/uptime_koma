package dev.haasele.koma.shared.notify

import android.content.Context

/** Application context for Android-only system intents (notification settings, etc.). */
object AndroidAppContext {
    @Volatile
    var application: Context? = null
        private set

    fun install(context: Context) {
        application = context.applicationContext
    }
}
