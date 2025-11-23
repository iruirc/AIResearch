package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant

/**
 * Exposed table definition for AssistantPipelines
 * Maps to the assistant_pipelines table in PostgreSQL
 */
object AssistantPipelinesTable : Table("assistant_pipelines") {
    val id = text("id")
    val name = text("name")
    val description = text("description").default("")
    val assistantIds = jsonb<List<String>>("assistant_ids")
    val providerId = customEnumeration(
        name = "provider_id",
        sql = "provider_type",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    )
    val model = text("model").nullable()
    val defaultParameters = jsonb<Map<String, Any>>("default_parameters").default(emptyMap())
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}
