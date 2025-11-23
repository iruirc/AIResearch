package com.researchai.persistence.sql

import com.researchai.models.Assistant
import com.researchai.persistence.AssistantStorage
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.AssistantsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation of AssistantStorage
 * Uses Exposed ORM for type-safe database queries
 */
class PostgresAssistantStorage : AssistantStorage {
    private val logger = LoggerFactory.getLogger(PostgresAssistantStorage::class.java)

    override suspend fun saveAssistant(assistant: Assistant): Result<Unit> = dbQuery {
        try {
            AssistantsTable.upsert {
                it[id] = assistant.id
                it[name] = assistant.name
                it[systemPrompt] = assistant.systemPrompt
                it[description] = assistant.description
                it[isSystem] = assistant.isSystem
                it[createdAt] = Clock.System.now()
            }
            logger.debug("Saved assistant: ${assistant.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save assistant: ${assistant.id}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadAssistant(assistantId: String): Result<Assistant?> = dbQuery {
        try {
            val row = AssistantsTable.selectAll()
                .where { AssistantsTable.id eq assistantId }
                .singleOrNull()

            val assistant = row?.let {
                Assistant(
                    id = it[AssistantsTable.id],
                    name = it[AssistantsTable.name],
                    systemPrompt = it[AssistantsTable.systemPrompt],
                    description = it[AssistantsTable.description],
                    isSystem = it[AssistantsTable.isSystem]
                )
            }

            logger.debug("Loaded assistant: $assistantId, found: ${assistant != null}")
            Result.success(assistant)
        } catch (e: Exception) {
            logger.error("Failed to load assistant: $assistantId", e)
            Result.failure(e)
        }
    }

    override suspend fun loadAllAssistants(): Result<List<Assistant>> = dbQuery {
        try {
            val assistants = AssistantsTable.selectAll()
                .map { row ->
                    Assistant(
                        id = row[AssistantsTable.id],
                        name = row[AssistantsTable.name],
                        systemPrompt = row[AssistantsTable.systemPrompt],
                        description = row[AssistantsTable.description],
                        isSystem = row[AssistantsTable.isSystem]
                    )
                }

            logger.debug("Loaded ${assistants.size} assistants")
            Result.success(assistants)
        } catch (e: Exception) {
            logger.error("Failed to load all assistants", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteAssistant(assistantId: String): Result<Unit> = dbQuery {
        try {
            AssistantsTable.deleteWhere { id eq assistantId }
            logger.info("Deleted assistant: $assistantId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete assistant: $assistantId", e)
            Result.failure(e)
        }
    }

    override suspend fun assistantExists(assistantId: String): Boolean = dbQuery {
        AssistantsTable.selectAll()
            .where { AssistantsTable.id eq assistantId }
            .count() > 0
    }

    override suspend fun close() {
        logger.info("Closing PostgresAssistantStorage")
    }
}
