package com.researchai.persistence.sql.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for Assistants
 * Maps to the assistants table in PostgreSQL
 */
object AssistantsTable : Table("assistants") {
    val id = text("id")
    val name = text("name")
    val systemPrompt = text("system_prompt")
    val description = text("description").default("")
    val isSystem = bool("is_system").default(false)
    val createdAt = timestamp("created_at").default(Clock.System.now())

    override val primaryKey = PrimaryKey(id)
}
