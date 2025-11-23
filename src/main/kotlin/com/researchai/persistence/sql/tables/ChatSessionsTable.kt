package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant

/**
 * Exposed table definition for ChatSessions
 * Maps to the chat_sessions table in PostgreSQL
 */
object ChatSessionsTable : Table("chat_sessions") {
    val id = text("id")
    val title = text("title").nullable()
    val messages = jsonb<List<Map<String, Any>>>("messages").default(emptyList())
    val createdAt = timestamp("created_at").default(Instant.now())
    val lastAccessedAt = timestamp("last_accessed_at").default(Instant.now())
    val assistantId = text("assistant_id").references(AssistantsTable.id).nullable()
    val scheduledTaskId = text("scheduled_task_id").references(ScheduledTasksTable.id).nullable()
    val pipelineId = text("pipeline_id").references(AssistantPipelinesTable.id).nullable()
    val archivedMessages = jsonb<List<Map<String, Any>>>("archived_messages").default(emptyList())
    val compressionConfig = jsonb<Map<String, Any>>("compression_config").default(emptyMap())
    val compressionCount = integer("compression_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
