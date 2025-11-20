package com.researchai.persistence

import com.researchai.domain.models.AssistantPipeline
import com.researchai.domain.models.PipelineExecution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JSON-based implementation of AssistantPipelineStorage
 */
class JsonPipelineStorage(
    private val pipelinesDir: File = File("data/assistant_pipelines"),
    private val executionsDir: File = File("data/pipeline_executions")
) : AssistantPipelineStorage {

    private val logger = LoggerFactory.getLogger(JsonPipelineStorage::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Create directories
        if (!pipelinesDir.exists()) {
            pipelinesDir.mkdirs()
            logger.info("Created pipelines directory: ${pipelinesDir.absolutePath}")
        }
        if (!executionsDir.exists()) {
            executionsDir.mkdirs()
            logger.info("Created executions directory: ${executionsDir.absolutePath}")
        }
    }

    // Pipeline configurations

    override suspend fun savePipeline(pipeline: AssistantPipeline): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(pipelinesDir, "${pipeline.id}.json")
                val tempFile = File(pipelinesDir, "${pipeline.id}.tmp")

                tempFile.writeText(json.encodeToString(pipeline))
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)

                logger.debug("Saved pipeline ${pipeline.id}: ${pipeline.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to save pipeline ${pipeline.id}", e)
                Result.failure(e)
            }
        }

    override suspend fun loadPipeline(pipelineId: String): Result<AssistantPipeline?> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(pipelinesDir, "$pipelineId.json")
                if (!file.exists()) {
                    return@withContext Result.success(null)
                }

                val pipeline = json.decodeFromString<AssistantPipeline>(file.readText())
                logger.debug("Loaded pipeline $pipelineId: ${pipeline.name}")
                Result.success(pipeline)
            } catch (e: Exception) {
                logger.error("Failed to load pipeline $pipelineId", e)
                Result.failure(e)
            }
        }

    override suspend fun loadAllPipelines(): Result<List<AssistantPipeline>> =
        withContext(Dispatchers.IO) {
            try {
                val pipelines = mutableListOf<AssistantPipeline>()

                pipelinesDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val pipeline = json.decodeFromString<AssistantPipeline>(file.readText())
                        pipelines.add(pipeline)
                    } catch (e: Exception) {
                        logger.warn("Failed to load pipeline from ${file.name}", e)
                    }
                }

                // Sort by updatedAt descending
                pipelines.sortByDescending { it.updatedAt }

                logger.info("Loaded ${pipelines.size} pipelines")
                Result.success(pipelines)
            } catch (e: Exception) {
                logger.error("Failed to load pipelines", e)
                Result.failure(e)
            }
        }

    override suspend fun deletePipeline(pipelineId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(pipelinesDir, "$pipelineId.json")
                if (file.exists()) {
                    file.delete()
                    logger.info("Deleted pipeline $pipelineId")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to delete pipeline $pipelineId", e)
                Result.failure(e)
            }
        }

    override suspend fun pipelineExists(pipelineId: String): Boolean =
        withContext(Dispatchers.IO) {
            File(pipelinesDir, "$pipelineId.json").exists()
        }

    // Pipeline executions

    override suspend fun saveExecution(execution: PipelineExecution): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(executionsDir, "${execution.id}.json")
                val tempFile = File(executionsDir, "${execution.id}.tmp")

                tempFile.writeText(json.encodeToString(execution))
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)

                logger.debug("Saved execution ${execution.id}")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to save execution ${execution.id}", e)
                Result.failure(e)
            }
        }

    override suspend fun loadExecution(executionId: String): Result<PipelineExecution?> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(executionsDir, "$executionId.json")
                if (!file.exists()) {
                    return@withContext Result.success(null)
                }

                val execution = json.decodeFromString<PipelineExecution>(file.readText())
                logger.debug("Loaded execution $executionId")
                Result.success(execution)
            } catch (e: Exception) {
                logger.error("Failed to load execution $executionId", e)
                Result.failure(e)
            }
        }

    override suspend fun loadExecutionsByPipeline(
        pipelineId: String,
        limit: Int
    ): Result<List<PipelineExecution>> = withContext(Dispatchers.IO) {
        try {
            val executions = mutableListOf<PipelineExecution>()

            executionsDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val execution = json.decodeFromString<PipelineExecution>(file.readText())
                    if (execution.pipelineId == pipelineId) {
                        executions.add(execution)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load execution from ${file.name}", e)
                }
            }

            // Sort by startTime descending and limit
            executions.sortByDescending { it.startTime }
            val limited = executions.take(limit)

            logger.debug("Loaded ${limited.size} executions for pipeline $pipelineId")
            Result.success(limited)
        } catch (e: Exception) {
            logger.error("Failed to load executions for pipeline $pipelineId", e)
            Result.failure(e)
        }
    }

    override suspend fun loadRecentExecutions(limit: Int): Result<List<PipelineExecution>> =
        withContext(Dispatchers.IO) {
            try {
                val executions = mutableListOf<PipelineExecution>()

                executionsDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val execution = json.decodeFromString<PipelineExecution>(file.readText())
                        executions.add(execution)
                    } catch (e: Exception) {
                        logger.warn("Failed to load execution from ${file.name}", e)
                    }
                }

                // Sort by startTime descending and limit
                executions.sortByDescending { it.startTime }
                val limited = executions.take(limit)

                logger.debug("Loaded ${limited.size} recent executions")
                Result.success(limited)
            } catch (e: Exception) {
                logger.error("Failed to load recent executions", e)
                Result.failure(e)
            }
        }

    override suspend fun deleteExecution(executionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(executionsDir, "$executionId.json")
                if (file.exists()) {
                    file.delete()
                    logger.info("Deleted execution $executionId")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to delete execution $executionId", e)
                Result.failure(e)
            }
        }

    override suspend fun close() {
        logger.info("Closing JSON pipeline storage")
    }
}
