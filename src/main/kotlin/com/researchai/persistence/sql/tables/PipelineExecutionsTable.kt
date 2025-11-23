package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant

/**
 * Exposed table definition for PipelineExecutions
 * Maps to the pipeline_executions table in PostgreSQL
 */
object PipelineExecutionsTable : Table("pipeline_executions") {
    val id = text("id")
    val pipelineId = text("pipeline_id").references(AssistantPipelinesTable.id).nullable()
    val pipelineName = text("pipeline_name")
    val sessionId = text("session_id").references(ChatSessionsTable.id)
    val initialMessage = text("initial_message")
    val assistantIds = jsonb<List<String>>("assistant_ids")
    val providerId = customEnumeration(
        name = "provider_id",
        sql = "provider_type",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    )
    val model = text("model")
    val parameters = jsonb<Map<String, Any>>("parameters").default(emptyMap())
    val steps = jsonb<List<Map<String, Any>>>("steps").default(emptyList())
    val status = customEnumeration(
        name = "status",
        sql = "pipeline_status",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    )
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time").nullable()
    val error = jsonb<Map<String, Any>>("error").nullable()

    override val primaryKey = PrimaryKey(id)
}
