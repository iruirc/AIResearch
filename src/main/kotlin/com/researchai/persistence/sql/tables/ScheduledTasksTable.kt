package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Exposed table definition for ScheduledTasks
 * Maps to the scheduled_tasks table in PostgreSQL
 */
object ScheduledTasksTable : Table("scheduled_tasks") {
    val id = text("id")
    val title = text("title")
    val taskRequest = text("task_request")
    val intervalSeconds = long("interval_seconds")
    val executeImmediately = bool("execute_immediately").default(false)
    val providerId = customEnumeration(
        name = "provider_id",
        sql = "provider_type",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    ).nullable()
    val model = text("model").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}
