package dev.haasele.koma.shared.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.haasele.koma.shared.db.shared.newInstance
import dev.haasele.koma.shared.db.shared.schema
import kotlin.Unit

public interface KomaDatabase : Transacter {
  public val heartbeatQueries: HeartbeatQueries

  public val infrastructureQueries: InfrastructureQueries

  public val maintenanceQueries: MaintenanceQueries

  public val monitorQueries: MonitorQueries

  public val notificationChannelQueries: NotificationChannelQueries

  public val settingQueries: SettingQueries

  public val statQueries: StatQueries

  public val statusPageQueries: StatusPageQueries

  public val tagQueries: TagQueries

  public val userQueries: UserQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = KomaDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): KomaDatabase = KomaDatabase::class.newInstance(driver)
  }
}
