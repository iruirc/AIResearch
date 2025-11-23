package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for ScheduledTasks
 * Maps to the scheduled_tasks table in PostgreSQL
 */
object ScheduledTasksTable : Table("scheduled_tasks") {
    val id = text("id")
    val title = text("title").nullable()
    val taskRequest = text("task_request")
    val intervalSeconds = long("interval_seconds")
    val executeImmediately = bool("execute_immediately").default(false)
    val providerId = text("provider_id").nullable()
    val model = text("model").nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(id)
}
