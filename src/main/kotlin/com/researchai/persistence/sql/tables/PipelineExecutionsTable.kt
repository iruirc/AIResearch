package com.researchai.persistence.sql.tables

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for PipelineExecutions
 * Maps to the pipeline_executions table in PostgreSQL
 */
object PipelineExecutionsTable : Table("pipeline_executions") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = text("id")
    val pipelineId = text("pipeline_id").references(AssistantPipelinesTable.id).nullable()
    val pipelineName = text("pipeline_name")
    val sessionId = text("session_id").references(ChatSessionsTable.id)
    val initialMessage = text("initial_message")
    val assistantIds = jsonb<List<String>>("assistant_ids", json)
    val providerId = text("provider_id")
    val model = text("model")
    val parameters = jsonb<Map<String, Any>>("parameters", json).default(emptyMap())
    val steps = jsonb<List<Map<String, Any>>>("steps", json).default(emptyList())
    val status = text("status")
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time").nullable()
    val error = jsonb<Map<String, Any>>("error", json).nullable()

    override val primaryKey = PrimaryKey(id)
}
