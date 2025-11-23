package com.researchai.persistence.sql

import com.researchai.domain.models.PipelineExecution
import com.researchai.domain.models.PipelineStatus
import com.researchai.domain.models.ProviderType
import com.researchai.persistence.sql.DatabaseFactory.dbQuery
import com.researchai.persistence.sql.tables.PipelineExecutionsTable
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * PostgreSQL implementation for PipelineExecution storage
 * Handles JSONB storage for assistantIds, parameters, steps, and error
 */
class PostgresExecutionStorage {
    private val logger = LoggerFactory.getLogger(PostgresExecutionStorage::class.java)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Saves a pipeline execution to the database
     */
    suspend fun saveExecution(execution: PipelineExecution): Result<Unit> = dbQuery {
        try {
            // Convert complex objects to JSONB-compatible Maps
            val parametersJson = json.encodeToString(execution.parameters)
            val stepsJson = json.encodeToString(execution.steps)
            val errorJson = execution.error?.let { json.encodeToString(it) }

            PipelineExecutionsTable.upsert {
                it[id] = execution.id
                it[pipelineId] = execution.pipelineId
                it[pipelineName] = execution.pipelineName
                it[sessionId] = execution.sessionId
                it[initialMessage] = execution.initialMessage
                it[assistantIds] = execution.assistantIds
                it[providerId] = execution.providerId.toString()
                it[model] = execution.model
                it[parameters] = json.decodeFromString<Map<String, Any>>(parametersJson)
                it[steps] = json.decodeFromString<List<Map<String, Any>>>(stepsJson)
                it[status] = execution.status.toString()
                it[startTime] = java.time.Instant.ofEpochMilli(execution.startTime)
                it[endTime] = execution.endTime?.let { time -> java.time.Instant.ofEpochMilli(time) }
                it[error] = errorJson?.let { errJson -> json.decodeFromString<Map<String, Any>>(errJson) }
            }

            logger.debug("Saved execution: ${execution.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to save execution ${execution.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Loads an execution from the database by ID
     */
    suspend fun loadExecution(executionId: String): Result<PipelineExecution?> = dbQuery {
        try {
            val row = PipelineExecutionsTable.selectAll()
                .where { PipelineExecutionsTable.id eq executionId }
                .singleOrNull()

            val execution = row?.let {
                // Reconstruct complex objects from JSONB
                val parametersData = it[PipelineExecutionsTable.parameters]
                val parametersJson = json.encodeToString(parametersData)
                val parameters = json.decodeFromString<Map<String, Any>>(parametersJson)

                val stepsData = it[PipelineExecutionsTable.steps]
                val stepsJson = json.encodeToString(stepsData)
                val steps = json.decodeFromString<List<Map<String, Any>>>(stepsJson)

                val errorData = it[PipelineExecutionsTable.error]
                val error = errorData?.let { errData ->
                    val errorJson = json.encodeToString(errData)
                    json.decodeFromString<Map<String, String>>(errorJson)
                }

                PipelineExecution(
                    id = it[PipelineExecutionsTable.id],
                    pipelineId = it[PipelineExecutionsTable.pipelineId],
                    pipelineName = it[PipelineExecutionsTable.pipelineName],
                    sessionId = it[PipelineExecutionsTable.sessionId],
                    initialMessage = it[PipelineExecutionsTable.initialMessage],
                    assistantIds = it[PipelineExecutionsTable.assistantIds],
                    providerId = ProviderType.valueOf(it[PipelineExecutionsTable.providerId]),
                    model = it[PipelineExecutionsTable.model],
                    parameters = parameters,
                    steps = steps,
                    status = PipelineStatus.valueOf(it[PipelineExecutionsTable.status]),
                    startTime = it[PipelineExecutionsTable.startTime].toEpochMilli(),
                    endTime = it[PipelineExecutionsTable.endTime]?.toEpochMilli(),
                    error = error
                )
            }

            logger.debug("Loaded execution: $executionId, found: ${execution != null}")
            Result.success(execution)
        } catch (e: Exception) {
            logger.error("Failed to load execution $executionId", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all executions for a specific pipeline
     */
    suspend fun loadExecutionsByPipeline(pipelineId: String): Result<List<PipelineExecution>> = dbQuery {
        try {
            val executions = PipelineExecutionsTable.selectAll()
                .where { PipelineExecutionsTable.pipelineId eq pipelineId }
                .orderBy(PipelineExecutionsTable.startTime, SortOrder.DESC)
                .map { row ->
                    // Reconstruct complex objects from JSONB
                    val parametersData = row[PipelineExecutionsTable.parameters]
                    val parametersJson = json.encodeToString(parametersData)
                    val parameters = json.decodeFromString<Map<String, Any>>(parametersJson)

                    val stepsData = row[PipelineExecutionsTable.steps]
                    val stepsJson = json.encodeToString(stepsData)
                    val steps = json.decodeFromString<List<Map<String, Any>>>(stepsJson)

                    val errorData = row[PipelineExecutionsTable.error]
                    val error = errorData?.let { errData ->
                        val errorJson = json.encodeToString(errData)
                        json.decodeFromString<Map<String, String>>(errorJson)
                    }

                    PipelineExecution(
                        id = row[PipelineExecutionsTable.id],
                        pipelineId = row[PipelineExecutionsTable.pipelineId],
                        pipelineName = row[PipelineExecutionsTable.pipelineName],
                        sessionId = row[PipelineExecutionsTable.sessionId],
                        initialMessage = row[PipelineExecutionsTable.initialMessage],
                        assistantIds = row[PipelineExecutionsTable.assistantIds],
                        providerId = ProviderType.valueOf(row[PipelineExecutionsTable.providerId]),
                        model = row[PipelineExecutionsTable.model],
                        parameters = parameters,
                        steps = steps,
                        status = PipelineStatus.valueOf(row[PipelineExecutionsTable.status]),
                        startTime = row[PipelineExecutionsTable.startTime].toEpochMilli(),
                        endTime = row[PipelineExecutionsTable.endTime]?.toEpochMilli(),
                        error = error
                    )
                }

            logger.debug("Loaded ${executions.size} executions for pipeline $pipelineId")
            Result.success(executions)
        } catch (e: Exception) {
            logger.error("Failed to load executions for pipeline $pipelineId", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all executions for a specific session
     */
    suspend fun loadExecutionsBySession(sessionId: String): Result<List<PipelineExecution>> = dbQuery {
        try {
            val executions = PipelineExecutionsTable.selectAll()
                .where { PipelineExecutionsTable.sessionId eq sessionId }
                .orderBy(PipelineExecutionsTable.startTime, SortOrder.DESC)
                .map { row ->
                    // Reconstruct complex objects from JSONB
                    val parametersData = row[PipelineExecutionsTable.parameters]
                    val parametersJson = json.encodeToString(parametersData)
                    val parameters = json.decodeFromString<Map<String, Any>>(parametersJson)

                    val stepsData = row[PipelineExecutionsTable.steps]
                    val stepsJson = json.encodeToString(stepsData)
                    val steps = json.decodeFromString<List<Map<String, Any>>>(stepsJson)

                    val errorData = row[PipelineExecutionsTable.error]
                    val error = errorData?.let { errData ->
                        val errorJson = json.encodeToString(errData)
                        json.decodeFromString<Map<String, String>>(errorJson)
                    }

                    PipelineExecution(
                        id = row[PipelineExecutionsTable.id],
                        pipelineId = row[PipelineExecutionsTable.pipelineId],
                        pipelineName = row[PipelineExecutionsTable.pipelineName],
                        sessionId = row[PipelineExecutionsTable.sessionId],
                        initialMessage = row[PipelineExecutionsTable.initialMessage],
                        assistantIds = row[PipelineExecutionsTable.assistantIds],
                        providerId = ProviderType.valueOf(row[PipelineExecutionsTable.providerId]),
                        model = row[PipelineExecutionsTable.model],
                        parameters = parameters,
                        steps = steps,
                        status = PipelineStatus.valueOf(row[PipelineExecutionsTable.status]),
                        startTime = row[PipelineExecutionsTable.startTime].toEpochMilli(),
                        endTime = row[PipelineExecutionsTable.endTime]?.toEpochMilli(),
                        error = error
                    )
                }

            logger.debug("Loaded ${executions.size} executions for session $sessionId")
            Result.success(executions)
        } catch (e: Exception) {
            logger.error("Failed to load executions for session $sessionId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes an execution from the database
     */
    suspend fun deleteExecution(executionId: String): Result<Unit> = dbQuery {
        try {
            PipelineExecutionsTable.deleteWhere { id eq executionId }
            logger.info("Deleted execution: $executionId")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to delete execution $executionId", e)
            Result.failure(e)
        }
    }
}
