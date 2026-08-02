package dev.haasele.koma.app.android

import android.app.Application
import android.content.Context
import android.util.Log
import dev.haasele.koma.shared.KomaCore
import dev.haasele.koma.shared.data.AndroidDatabaseDriverFactory
import dev.haasele.koma.shared.notify.AndroidNotifier
import kotlinx.coroutines.Dispatchers
import java.net.BindException

class KomaApplication : Application() {

    val core: KomaCore by lazy {
        KomaCore.create(
            driverFactory = AndroidDatabaseDriverFactory(this),
            parentContext = Dispatchers.Default,
            localNotifier = AndroidNotifier(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Ktor may surface BindException on a worker thread outside our scopes; keep the UI alive.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (error is BindException || generateSequence(error) { it.cause }.any { it is BindException }) {
                Log.w(TAG, "Suppressed BindException on ${thread.name}", error)
                return@setDefaultUncaughtExceptionHandler
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        const val TAG = "KomaApp"
    }
}

val Context.komaCore: KomaCore
    get() = (applicationContext as KomaApplication).core
