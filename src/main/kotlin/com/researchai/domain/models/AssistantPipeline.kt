package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Конфигурация pipeline ассистентов
 */
@Serializable
data class AssistantPipeline(
    val id: String,
    val name: String,
    val description: String,
    val assistantIds: List<String>, // Sequential order
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null, // Optional: override default model
    val defaultParameters: RequestParameters = RequestParameters(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(assistantIds.isNotEmpty()) { "Pipeline must have at least one assistant" }
        require(assistantIds.size <= MAX_PIPELINE_LENGTH) {
            "Pipeline length exceeds maximum ($MAX_PIPELINE_LENGTH)"
        }
        require(name.isNotBlank()) { "Pipeline name cannot be blank" }
    }

    companion object {
        const val MAX_PIPELINE_LENGTH = 10
    }
}

/**
 * Запрос на выполнение pipeline
 */
@Serializable
data class ExecutePipelineRequest(
    val initialMessage: String,
    val pipelineId: String? = null, // If provided, use saved pipeline
    val assistantIds: List<String>? = null, // If pipelineId is null, use this
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null,
    val parameters: RequestParameters = RequestParameters(),
    val savePipeline: Boolean = false, // Save as new pipeline config
    val pipelineName: String? = null, // Required if savePipeline = true
    val pipelineDescription: String? = null
) {
    init {
        require(pipelineId != null || assistantIds != null) {
            "Either pipelineId or assistantIds must be provided"
        }
        if (savePipeline) {
            require(!pipelineName.isNullOrBlank()) {
                "pipelineName is required when savePipeline = true"
            }
        }
    }
}

/**
 * Результат выполнения pipeline
 */
@Serializable
data class PipelineExecutionResult(
    val executionId: String,
    val pipelineId: String?,
    val sessionId: String,
    val finalOutput: String,
    val steps: List<AssistantStepResult>,
    val status: PipelineExecutionStatus,
    val totalTokens: TokenUsage,
    val totalExecutionTimeMs: Long,
    val startTime: Long,
    val endTime: Long,
    val error: String? = null
)

/**
 * Результат выполнения одного шага
 */
@Serializable
data class AssistantStepResult(
    val stepIndex: Int,
    val assistantId: String,
    val assistantName: String,
    val input: String,
    val output: String,
    val tokensUsed: TokenUsage,
    val executionTimeMs: Long,
    val timestamp: Long,
    val error: String? = null
)

/**
 * Статус выполнения pipeline
 */
@Serializable
enum class PipelineExecutionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    PARTIAL // Some steps completed before failure
}

/**
 * Краткая информация о pipeline для списка
 */
@Serializable
data class PipelineListItem(
    val id: String,
    val name: String,
    val description: String,
    val assistantCount: Int,
    val providerId: ProviderType,
    val createdAt: Long,
    val updatedAt: Long
)
