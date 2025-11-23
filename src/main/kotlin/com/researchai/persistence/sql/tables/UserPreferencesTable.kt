package com.researchai.persistence.sql.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Exposed table definition for UserPreferences
 * Maps to the user_preferences table in PostgreSQL
 */
object UserPreferencesTable : Table("user_preferences") {
    val id = integer("id").autoIncrement()
    val userId = text("user_id").references(UsersTable.id).uniqueIndex()
    val providerId = text("provider_id") // AI provider (not FK, stores string)
    val model = text("model")
    val temperature = double("temperature").default(1.0)
    val maxTokens = integer("max_tokens").default(4096)
    val format = customEnumeration(
        name = "format",
        sql = "response_format",
        fromDb = { value -> value as String },
        toDb = { it.toString() }
    ).default("PLAIN_TEXT")
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}
