package com.researchai.persistence.sql

import com.researchai.domain.models.AssistantPipeline
import com.researchai.domain.models.ProviderType
import com.researchai.domain.models.RequestParameters
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.AssistantPipelinesTable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation for AssistantPipeline storage
 * Uses Exposed ORM with JSONB for complex fields (assistantIds, defaultParameters)
 */
class PostgresPipelineStorage {
    private val logger = LoggerFactory.getLogger(PostgresPipelineStorage::class.java)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Saves a pipeline to the database
     */
    suspend fun savePipeline(pipeline: AssistantPipeline): Result<Unit> = dbQuery {
        try {
            val parametersElement = json.parseToJsonElement(json.encodeToString(pipeline.defaultParameters))

            AssistantPipelinesTable.upsert {
                it[id] = pipeline.id
                it[name] = pipeline.name
                it[description] = pipeline.description
                it[assistantIds] = pipeline.assistantIds
                it[providerId] = pipeline.providerId.toString()
                it[model] = pipeline.model
                it[defaultParameters] = parametersElement
                it[createdAt] = Instant.fromEpochMilliseconds(pipeline.createdAt)
                it[updatedAt] = Clock.System.now()
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
                    providerId = ProviderType.valueOf(it[AssistantPipelinesTable.providerId]),
                    model = it[AssistantPipelinesTable.model],
                    defaultParameters = json.decodeFromJsonElement(
                        RequestParameters.serializer(),
                        it[AssistantPipelinesTable.defaultParameters]
                    ),
                    createdAt = it[AssistantPipelinesTable.createdAt].toEpochMilliseconds(),
                    updatedAt = it[AssistantPipelinesTable.updatedAt].toEpochMilliseconds()
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
                        providerId = ProviderType.valueOf(row[AssistantPipelinesTable.providerId]),
                        model = row[AssistantPipelinesTable.model],
                        defaultParameters = json.decodeFromJsonElement(
                            RequestParameters.serializer(),
                            row[AssistantPipelinesTable.defaultParameters]
                        ),
                        createdAt = row[AssistantPipelinesTable.createdAt].toEpochMilliseconds(),
                        updatedAt = row[AssistantPipelinesTable.updatedAt].toEpochMilliseconds()
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
