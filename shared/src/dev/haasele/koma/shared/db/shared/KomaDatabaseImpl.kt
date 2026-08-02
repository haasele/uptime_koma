package dev.haasele.koma.shared.db.shared

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.haasele.koma.shared.db.HeartbeatQueries
import dev.haasele.koma.shared.db.InfrastructureQueries
import dev.haasele.koma.shared.db.KomaDatabase
import dev.haasele.koma.shared.db.MaintenanceQueries
import dev.haasele.koma.shared.db.MonitorQueries
import dev.haasele.koma.shared.db.NotificationChannelQueries
import dev.haasele.koma.shared.db.SettingQueries
import dev.haasele.koma.shared.db.StatQueries
import dev.haasele.koma.shared.db.StatusPageQueries
import dev.haasele.koma.shared.db.TagQueries
import dev.haasele.koma.shared.db.UserQueries
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<KomaDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = KomaDatabaseImpl.Schema

internal fun KClass<KomaDatabase>.newInstance(driver: SqlDriver): KomaDatabase = KomaDatabaseImpl(driver)

private class KomaDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver),
    KomaDatabase {
  override val heartbeatQueries: HeartbeatQueries = HeartbeatQueries(driver)

  override val infrastructureQueries: InfrastructureQueries = InfrastructureQueries(driver)

  override val maintenanceQueries: MaintenanceQueries = MaintenanceQueries(driver)

  override val monitorQueries: MonitorQueries = MonitorQueries(driver)

  override val notificationChannelQueries: NotificationChannelQueries =
      NotificationChannelQueries(driver)

  override val settingQueries: SettingQueries = SettingQueries(driver)

  override val statQueries: StatQueries = StatQueries(driver)

  override val statusPageQueries: StatusPageQueries = StatusPageQueries(driver)

  override val tagQueries: TagQueries = TagQueries(driver)

  override val userQueries: UserQueries = UserQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE heartbeat (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    monitor_id INTEGER NOT NULL,
          |    status INTEGER NOT NULL,
          |    msg TEXT NOT NULL DEFAULT '',
          |    ping_ms INTEGER,
          |    important INTEGER NOT NULL DEFAULT 0,
          |    time_ms INTEGER NOT NULL,
          |    duration_seconds INTEGER NOT NULL DEFAULT 0,
          |    retries INTEGER NOT NULL DEFAULT 0,
          |    down_count INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE proxy (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    protocol TEXT NOT NULL DEFAULT 'http',
          |    host TEXT NOT NULL,
          |    port INTEGER NOT NULL,
          |    username TEXT,
          |    password TEXT,
          |    active INTEGER NOT NULL DEFAULT 1,
          |    is_default INTEGER NOT NULL DEFAULT 0,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE docker_host (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    connection_type TEXT NOT NULL DEFAULT 'socket',
          |    daemon TEXT NOT NULL DEFAULT '/var/run/docker.sock',
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE api_key (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    key_hash TEXT NOT NULL,
          |    prefix TEXT NOT NULL,
          |    active INTEGER NOT NULL DEFAULT 1,
          |    expires_at INTEGER,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE maintenance (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    title TEXT NOT NULL,
          |    description TEXT,
          |    strategy TEXT NOT NULL DEFAULT 'single',
          |    active INTEGER NOT NULL DEFAULT 1,
          |    manual_active INTEGER NOT NULL DEFAULT 0,
          |    timezone TEXT,
          |    start_ms INTEGER,
          |    end_ms INTEGER,
          |    start_time TEXT,
          |    end_time TEXT,
          |    weekdays TEXT,
          |    days_of_month TEXT,
          |    cron TEXT,
          |    duration_minutes INTEGER,
          |    interval_day INTEGER,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE maintenance_monitor (
          |    maintenance_id INTEGER NOT NULL,
          |    monitor_id INTEGER NOT NULL,
          |    PRIMARY KEY (maintenance_id, monitor_id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE maintenance_status_page (
          |    maintenance_id INTEGER NOT NULL,
          |    status_page_id INTEGER NOT NULL,
          |    PRIMARY KEY (maintenance_id, status_page_id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE monitor (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    type TEXT NOT NULL,
          |    active INTEGER NOT NULL DEFAULT 1,
          |    parent_id INTEGER,
          |    description TEXT,
          |    interval_seconds INTEGER NOT NULL DEFAULT 60,
          |    retry_interval_seconds INTEGER NOT NULL DEFAULT 60,
          |    resend_interval INTEGER NOT NULL DEFAULT 0,
          |    max_retries INTEGER NOT NULL DEFAULT 0,
          |    timeout_seconds INTEGER NOT NULL DEFAULT 48,
          |    upside_down INTEGER NOT NULL DEFAULT 0,
          |    push_token TEXT,
          |    proxy_id INTEGER,
          |    weight INTEGER NOT NULL DEFAULT 2000,
          |    config TEXT NOT NULL DEFAULT '{}',
          |    created_at INTEGER NOT NULL,
          |    updated_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE notification_channel (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    provider TEXT NOT NULL,
          |    config TEXT NOT NULL DEFAULT '{}',
          |    active INTEGER NOT NULL DEFAULT 1,
          |    is_default INTEGER NOT NULL DEFAULT 0,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE monitor_notification (
          |    monitor_id INTEGER NOT NULL,
          |    notification_id INTEGER NOT NULL,
          |    PRIMARY KEY (monitor_id, notification_id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE setting (
          |    key TEXT NOT NULL PRIMARY KEY,
          |    value TEXT NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE stat_daily (
          |    monitor_id INTEGER NOT NULL,
          |    day_ms INTEGER NOT NULL,
          |    up INTEGER NOT NULL DEFAULT 0,
          |    down INTEGER NOT NULL DEFAULT 0,
          |    maintenance INTEGER NOT NULL DEFAULT 0,
          |    ping_sum REAL NOT NULL DEFAULT 0.0,
          |    ping_count INTEGER NOT NULL DEFAULT 0,
          |    ping_min REAL,
          |    ping_max REAL,
          |    PRIMARY KEY (monitor_id, day_ms)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE status_page (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    slug TEXT NOT NULL UNIQUE,
          |    title TEXT NOT NULL,
          |    description TEXT,
          |    icon TEXT,
          |    theme TEXT NOT NULL DEFAULT 'auto',
          |    published INTEGER NOT NULL DEFAULT 1,
          |    show_tags INTEGER NOT NULL DEFAULT 0,
          |    show_uptime_percentage INTEGER NOT NULL DEFAULT 1,
          |    show_certificate_expiry INTEGER NOT NULL DEFAULT 0,
          |    footer_text TEXT,
          |    accent_color TEXT,
          |    password_hash TEXT,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE status_page_group (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    status_page_id INTEGER NOT NULL,
          |    name TEXT NOT NULL,
          |    weight INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE status_page_group_monitor (
          |    group_id INTEGER NOT NULL,
          |    monitor_id INTEGER NOT NULL,
          |    weight INTEGER NOT NULL DEFAULT 0,
          |    PRIMARY KEY (group_id, monitor_id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE incident (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    status_page_id INTEGER NOT NULL,
          |    title TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    style TEXT NOT NULL DEFAULT 'warning',
          |    pin INTEGER NOT NULL DEFAULT 1,
          |    created_at INTEGER NOT NULL,
          |    last_updated_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE tag (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    color TEXT NOT NULL DEFAULT '#4B5563'
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE monitor_tag (
          |    monitor_id INTEGER NOT NULL,
          |    tag_id INTEGER NOT NULL,
          |    value TEXT,
          |    PRIMARY KEY (monitor_id, tag_id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE user (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    username TEXT NOT NULL UNIQUE,
          |    password_hash TEXT NOT NULL,
          |    twofa_secret TEXT,
          |    twofa_enabled INTEGER NOT NULL DEFAULT 0,
          |    created_at INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, "CREATE INDEX heartbeat_monitor_time_idx ON heartbeat(monitor_id, time_ms)", 0)
      driver.execute(null, "CREATE INDEX heartbeat_important_idx ON heartbeat(monitor_id, important, time_ms)", 0)
      driver.execute(null, "CREATE INDEX monitor_push_token_idx ON monitor(push_token)", 0)
      driver.execute(null, "CREATE INDEX monitor_parent_idx ON monitor(parent_id)", 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
