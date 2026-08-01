package dev.haasele.koma.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.domain.AppSettings
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_CHECK_UPDATE
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_EMBEDDED_SERVER_ENABLED
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_EMBEDDED_SERVER_PORT
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_ENTRY_PAGE
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_KEEP_HEARTBEAT_DAYS
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_LANGUAGE
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_METRICS_ENABLED
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_NOTIFICATION_SOUND
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_PRIMARY_BASE_URL
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_REMOTE_ACCESS_ENABLED
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_REMOTE_ACCESS_TOKEN
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_START_ENGINE_ON_LAUNCH
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_THEME
import dev.haasele.koma.shared.domain.AppSettings.Companion.KEY_TIMEZONE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(private val db: KomaDatabase) {

    private val queries get() = db.settingQueries

    fun observe(): Flow<AppSettings> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            toSettings(rows.associate { it.key to it.value_ })
        }

    suspend fun get(): AppSettings = withContext(ioDispatcher) {
        toSettings(queries.selectAll().executeAsList().associate { it.key to it.value_ })
    }

    suspend fun getRaw(key: String): String? = withContext(ioDispatcher) {
        queries.selectByKey(key).executeAsOneOrNull()
    }

    suspend fun putRaw(key: String, value: String) = withContext(ioDispatcher) {
        queries.upsert(key, value)
    }

    suspend fun save(settings: AppSettings) = withContext(ioDispatcher) {
        db.transaction {
            queries.upsert(KEY_CHECK_UPDATE, settings.checkUpdate.toString())
            queries.upsert(KEY_KEEP_HEARTBEAT_DAYS, settings.keepHeartbeatDays.toString())
            queries.upsert(KEY_ENTRY_PAGE, settings.entryPage)
            queries.upsert(KEY_PRIMARY_BASE_URL, settings.primaryBaseUrl)
            queries.upsert(KEY_TIMEZONE, settings.timezone)
            queries.upsert(KEY_THEME, settings.theme)
            queries.upsert(KEY_LANGUAGE, settings.language)
            queries.upsert(KEY_EMBEDDED_SERVER_ENABLED, settings.embeddedServerEnabled.toString())
            queries.upsert(KEY_EMBEDDED_SERVER_PORT, settings.embeddedServerPort.toString())
            queries.upsert(KEY_REMOTE_ACCESS_ENABLED, settings.remoteAccessEnabled.toString())
            queries.upsert(KEY_REMOTE_ACCESS_TOKEN, settings.remoteAccessToken)
            queries.upsert(KEY_METRICS_ENABLED, settings.metricsEnabled.toString())
            queries.upsert(KEY_NOTIFICATION_SOUND, settings.notificationSoundEnabled.toString())
            queries.upsert(KEY_START_ENGINE_ON_LAUNCH, settings.startEngineOnLaunch.toString())
        }
    }

    private fun toSettings(values: Map<String, String>): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            checkUpdate = values[KEY_CHECK_UPDATE]?.toBooleanStrictOrNull() ?: defaults.checkUpdate,
            keepHeartbeatDays = values[KEY_KEEP_HEARTBEAT_DAYS]?.toIntOrNull() ?: defaults.keepHeartbeatDays,
            entryPage = values[KEY_ENTRY_PAGE] ?: defaults.entryPage,
            primaryBaseUrl = values[KEY_PRIMARY_BASE_URL] ?: defaults.primaryBaseUrl,
            timezone = values[KEY_TIMEZONE] ?: defaults.timezone,
            theme = values[KEY_THEME] ?: defaults.theme,
            language = values[KEY_LANGUAGE] ?: defaults.language,
            embeddedServerEnabled = values[KEY_EMBEDDED_SERVER_ENABLED]?.toBooleanStrictOrNull()
                ?: defaults.embeddedServerEnabled,
            embeddedServerPort = values[KEY_EMBEDDED_SERVER_PORT]?.toIntOrNull() ?: defaults.embeddedServerPort,
            remoteAccessEnabled = values[KEY_REMOTE_ACCESS_ENABLED]?.toBooleanStrictOrNull()
                ?: defaults.remoteAccessEnabled,
            remoteAccessToken = values[KEY_REMOTE_ACCESS_TOKEN] ?: defaults.remoteAccessToken,
            metricsEnabled = values[KEY_METRICS_ENABLED]?.toBooleanStrictOrNull() ?: defaults.metricsEnabled,
            notificationSoundEnabled = values[KEY_NOTIFICATION_SOUND]?.toBooleanStrictOrNull()
                ?: defaults.notificationSoundEnabled,
            startEngineOnLaunch = values[KEY_START_ENGINE_ON_LAUNCH]?.toBooleanStrictOrNull()
                ?: defaults.startEngineOnLaunch,
        )
    }
}
