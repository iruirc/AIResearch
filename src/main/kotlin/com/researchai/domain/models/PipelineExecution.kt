package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Runtime состояние выполнения pipeline
 * Сохраняется для истории и отслеживания прогресса
 */
@Serializable
data class PipelineExecution(
    val id: String,
    val pipelineId: String?,
    val pipelineName: String,
    val sessionId: String,
    val initialMessage: String,
    val assistantIds: List<String>,
    val providerId: ProviderType,
    val model: String,
    val parameters: RequestParameters,
    val steps: List<AssistantStep>,
    val status: PipelineExecutionStatus,
    val startTime: Long,
    val endTime: Long? = null,
    val error: PipelineError? = null
) {
    /**
     * Получить результат выполнения
     */
    fun toResult(): PipelineExecutionResult {
        val stepResults = steps.map { it.toResult() }
        val totalTokens = TokenUsage(
            inputTokens = stepResults.sumOf { it.tokensUsed.inputTokens },
            outputTokens = stepResults.sumOf { it.tokensUsed.outputTokens },
            totalTokens = stepResults.sumOf { it.tokensUsed.totalTokens }
        )
        val totalTime = endTime?.let { it - startTime } ?: 0L
        val finalOutput = steps.lastOrNull()?.output ?: ""

        return PipelineExecutionResult(
            executionId = id,
            pipelineId = pipelineId,
            sessionId = sessionId,
            finalOutput = finalOutput,
            steps = stepResults,
            status = status,
            totalTokens = totalTokens,
            totalExecutionTimeMs = totalTime,
            startTime = startTime,
            endTime = endTime ?: System.currentTimeMillis(),
            error = error?.message
        )
    }
}

/**
 * Один шаг выполнения в pipeline
 */
@Serializable
data class AssistantStep(
    val stepIndex: Int,
    val assistantId: String,
    val assistantName: String,
    val input: String,
    val output: String,
    val tokensUsed: TokenUsage,
    val executionTimeMs: Long,
    val timestamp: Long,
    val error: PipelineError? = null
) {
    fun toResult(): AssistantStepResult {
        return AssistantStepResult(
            stepIndex = stepIndex,
            assistantId = assistantId,
            assistantName = assistantName,
            input = input,
            output = output,
            tokensUsed = tokensUsed,
            executionTimeMs = executionTimeMs,
            timestamp = timestamp,
            error = error?.message
        )
    }
}

/**
 * Ошибка выполнения pipeline
 */
@Serializable
data class PipelineError(
    val message: String,
    val stepIndex: Int? = null,
    val assistantId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
