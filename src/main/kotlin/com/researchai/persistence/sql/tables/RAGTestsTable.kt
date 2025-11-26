package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for RAG Tests
 * Maps to the rag_tests table in PostgreSQL
 */
object RAGTestsTable : Table("rag_tests") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = text("id")
    val name = text("name").uniqueIndex()
    val queries = jsonb<List<JsonElement>>("queries", json).default(emptyList())
    val evaluationMetrics = jsonb<JsonElement>("evaluation_metrics", json).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(id)
}
