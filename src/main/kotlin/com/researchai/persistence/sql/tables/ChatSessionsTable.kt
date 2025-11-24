package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for ChatSessions
 * Maps to the chat_sessions table in PostgreSQL
 */
object ChatSessionsTable : Table("chat_sessions") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = text("id")
    val title = text("title").nullable()
    val messages = jsonb<List<JsonElement>>("messages", json).default(emptyList())
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val lastAccessedAt = timestamp("last_accessed_at").default(Clock.System.now())
    val assistantId = text("assistant_id").references(AssistantsTable.id).nullable()
    val scheduledTaskId = text("scheduled_task_id").references(ScheduledTasksTable.id).nullable()
    val pipelineId = text("pipeline_id").references(AssistantPipelinesTable.id).nullable()
    val archivedMessages = jsonb<List<JsonElement>>("archived_messages", json).default(emptyList())
    val compressionConfig = jsonb<JsonElement>("compression_config", json).default(Json.parseToJsonElement("{}"))
    val compressionCount = integer("compression_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
