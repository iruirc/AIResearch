package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for AssistantPipelines
 * Maps to the assistant_pipelines table in PostgreSQL
 */
object AssistantPipelinesTable : Table("assistant_pipelines") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = text("id")
    val name = text("name")
    val description = text("description").default("")
    val assistantIds = jsonb<List<String>>("assistant_ids", json)
    val providerId = text("provider_id")
    val model = text("model").nullable()
    val defaultParameters = jsonb<Map<String, Any>>("default_parameters", json).default(emptyMap())
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(id)
}
