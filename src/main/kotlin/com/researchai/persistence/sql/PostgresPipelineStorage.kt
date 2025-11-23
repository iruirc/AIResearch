package com.researchai.persistence.sql

import com.researchai.domain.models.AssistantPipeline
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.AssistantPipelinesTable
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation for AssistantPipeline storage
 * Uses Exposed ORM with JSONB for complex fields (assistantIds, defaultParameters)
 */
class PostgresPipelineStorage {
    private val logger = LoggerFactory.getLogger(PostgresPipelineStorage::class.java)

    /**
     * Saves a pipeline to the database
     */
    suspend fun savePipeline(pipeline: AssistantPipeline): Result<Unit> = dbQuery {
        try {
            AssistantPipelinesTable.upsert {
                it[id] = pipeline.id
                it[name] = pipeline.name
                it[description] = pipeline.description
                it[assistantIds] = pipeline.assistantIds
                it[providerId] = pipeline.providerId.toString()
                it[model] = pipeline.model
                it[defaultParameters] = pipeline.defaultParameters
                it[createdAt] = java.time.Instant.ofEpochMilli(pipeline.createdAt)
                it[updatedAt] = java.time.Instant.now()
            }

            logger.debug("Saved pipeline: ${pipeline.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save pipeline ${pipeline.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a pipeline from the database by ID
     */
    suspend fun loadPipeline(pipelineId: String): Result<AssistantPipeline?> = dbQuery {
        try {
            val row = AssistantPipelinesTable.selectAll()
                .where { AssistantPipelinesTable.id eq pipelineId }
                .singleOrNull()

            val pipeline = row?.let {
                AssistantPipeline(
                    id = it[AssistantPipelinesTable.id],
                    name = it[AssistantPipelinesTable.name],
                    description = it[AssistantPipelinesTable.description],
                    assistantIds = it[AssistantPipelinesTable.assistantIds],
                    providerId = com.researchai.domain.models.ProviderType.valueOf(
                        it[AssistantPipelinesTable.providerId]
                    ),
                    model = it[AssistantPipelinesTable.model],
                    defaultParameters = it[AssistantPipelinesTable.defaultParameters],
                    createdAt = it[AssistantPipelinesTable.createdAt].toEpochMilli(),
                    updatedAt = it[AssistantPipelinesTable.updatedAt].toEpochMilli()
                )
            }

            logger.debug("Loaded pipeline: $pipelineId, found: ${pipeline != null}")
            Result.success(pipeline)
        } catch (e: Exception) {
            logger.error("Failed to load pipeline $pipelineId", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all pipelines from the database
     */
    suspend fun loadAllPipelines(): Result<List<AssistantPipeline>> = dbQuery {
        try {
            val pipelines = AssistantPipelinesTable.selectAll()
                .map { row ->
                    AssistantPipeline(
                        id = row[AssistantPipelinesTable.id],
                        name = row[AssistantPipelinesTable.name],
                        description = row[AssistantPipelinesTable.description],
                        assistantIds = row[AssistantPipelinesTable.assistantIds],
                        providerId = com.researchai.domain.models.ProviderType.valueOf(
                            row[AssistantPipelinesTable.providerId]
                        ),
                        model = row[AssistantPipelinesTable.model],
                        defaultParameters = row[AssistantPipelinesTable.defaultParameters],
                        createdAt = row[AssistantPipelinesTable.createdAt].toEpochMilli(),
                        updatedAt = row[AssistantPipelinesTable.updatedAt].toEpochMilli()
                    )
                }

            logger.debug("Loaded ${pipelines.size} pipelines")
            Result.success(pipelines)
        } catch (e: Exception) {
            logger.error("Failed to load all pipelines", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a pipeline from the database
     */
    suspend fun deletePipeline(pipelineId: String): Result<Unit> = dbQuery {
        try {
            AssistantPipelinesTable.deleteWhere { id eq pipelineId }
            logger.info("Deleted pipeline: $pipelineId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete pipeline $pipelineId", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a pipeline exists
     */
    suspend fun pipelineExists(pipelineId: String): Boolean = dbQuery {
        AssistantPipelinesTable.selectAll()
            .where { AssistantPipelinesTable.id eq pipelineId }
            .count() > 0
    }
}
