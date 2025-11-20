package com.researchai.persistence

import com.researchai.domain.models.AssistantPipeline
import com.researchai.domain.models.PipelineExecution

/**
 * Интерфейс для хранения конфигураций pipeline и истории выполнений
 */
interface AssistantPipelineStorage {

    // Pipeline configurations

    suspend fun savePipeline(pipeline: AssistantPipeline): Result<Unit>

    suspend fun loadPipeline(pipelineId: String): Result<AssistantPipeline?>

    suspend fun loadAllPipelines(): Result<List<AssistantPipeline>>

    suspend fun deletePipeline(pipelineId: String): Result<Unit>

    suspend fun pipelineExists(pipelineId: String): Boolean

    // Pipeline executions (history)

    suspend fun saveExecution(execution: PipelineExecution): Result<Unit>

    suspend fun loadExecution(executionId: String): Result<PipelineExecution?>

    suspend fun loadExecutionsByPipeline(
        pipelineId: String,
        limit: Int = 50
    ): Result<List<PipelineExecution>>

    suspend fun loadRecentExecutions(limit: Int = 50): Result<List<PipelineExecution>>

    suspend fun deleteExecution(executionId: String): Result<Unit>

    // Cleanup

    suspend fun close()
}
