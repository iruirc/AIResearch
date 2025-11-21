package com.researchai.domain.usecase

import com.researchai.domain.models.*
import com.researchai.domain.repository.SessionRepository
import com.researchai.persistence.AssistantPipelineStorage
import com.researchai.services.AssistantManager
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Use case для выполнения pipeline ассистентов
 */
class AssistantPipelineUseCase(
    private val sendMessageUseCase: SendMessageUseCase,
    private val assistantManager: AssistantManager,
    private val sessionRepository: SessionRepository,
    private val pipelineStorage: AssistantPipelineStorage
) {
    private val logger = LoggerFactory.getLogger(AssistantPipelineUseCase::class.java)

    companion object {
        private const val MAX_PIPELINE_EXECUTION_TIME_MS = 300_000L // 5 minutes
    }

    /**
     * Выполнить pipeline
     */
    suspend operator fun invoke(
        request: ExecutePipelineRequest
    ): Result<PipelineExecutionResult> {
        return try {
            val startTime = System.currentTimeMillis()

            logger.info("🚀 ============ PIPELINE EXECUTION STARTED ============")
            logger.info("   Request: pipelineId=${request.pipelineId}, message='${request.initialMessage}'")

            // 1. Resolve pipeline configuration
            val (assistantIds, pipelineId, pipelineName) = resolvePipelineConfig(request)
                .getOrElse { return Result.failure(it) }

            logger.info("Pipeline: $pipelineName, Assistants: ${assistantIds.size}")

            // 2. Validate assistants exist
            validateAssistants(assistantIds).getOrElse { return Result.failure(it) }

            // 3. Create session for pipeline execution
            val executionId = UUID.randomUUID().toString()
            val session = sessionRepository.createSession(request.providerId).getOrThrow()

            // Update session with pipeline info
            val updatedSession = session.copy(
                pipelineExecutionId = executionId,
                currentPipelineStep = 0
            )
            sessionRepository.updateSession(updatedSession).getOrThrow()

            logger.info("Created session ${session.id} for pipeline execution")

            // 4. Create execution object
            val execution = PipelineExecution(
                id = executionId,
                pipelineId = pipelineId,
                pipelineName = pipelineName,
                sessionId = session.id,
                initialMessage = request.initialMessage,
                assistantIds = assistantIds,
                providerId = request.providerId,
                model = request.model ?: getDefaultModel(request.providerId),
                parameters = request.parameters,
                steps = emptyList(),
                status = PipelineExecutionStatus.IN_PROGRESS,
                startTime = startTime
            )

            // 5. Execute pipeline steps
            val result = executePipelineSteps(
                execution = execution,
                request = request
            )

            // 6. Save execution history
            pipelineStorage.saveExecution(result).onFailure {
                logger.warn("Failed to save execution history", it)
            }

            // 7. Save pipeline config if requested
            if (request.savePipeline && request.pipelineName != null) {
                savePipelineConfig(request, assistantIds).onFailure {
                    logger.warn("Failed to save pipeline config", it)
                }
            }

            logger.info("Pipeline execution completed: ${result.status}")

            Result.success(result.toResult())
        } catch (e: Exception) {
            logger.error("Pipeline execution failed", e)
            Result.failure(AIError.fromException(e))
        }
    }

    /**
     * Выполнить шаги pipeline
     */
    private suspend fun executePipelineSteps(
        execution: PipelineExecution,
        request: ExecutePipelineRequest
    ): PipelineExecution {
        val steps = mutableListOf<AssistantStep>()
        var currentInput = request.initialMessage
        var currentExecution = execution

        for ((index, assistantId) in execution.assistantIds.withIndex()) {
            // Check timeout
            val elapsedTime = System.currentTimeMillis() - execution.startTime
            if (elapsedTime > MAX_PIPELINE_EXECUTION_TIME_MS) {
                val error = PipelineError(
                    message = "Pipeline execution timeout (${MAX_PIPELINE_EXECUTION_TIME_MS}ms)",
                    stepIndex = index
                )
                return currentExecution.copy(
                    steps = steps,
                    status = PipelineExecutionStatus.PARTIAL,
                    endTime = System.currentTimeMillis(),
                    error = error
                )
            }

            // Execute step
            val stepResult = executeStep(
                assistantId = assistantId,
                input = currentInput,
                stepIndex = index,
                execution = currentExecution,
                request = request
            )

            when (stepResult) {
                is StepResult.Success -> {
                    steps.add(stepResult.step)
                    currentInput = stepResult.step.output

                    // Update session step
                    val session = sessionRepository.getSession(execution.sessionId).getOrNull()
                    session?.let {
                        val updated = it.copy(currentPipelineStep = index + 1)
                        sessionRepository.updateSession(updated).onFailure { err ->
                            logger.warn("Failed to update session step", err)
                        }
                    }

                    logger.info("Step $index completed: $assistantId")
                }
                is StepResult.Failure -> {
                    steps.add(stepResult.step)

                    // Error handling: stop pipeline
                    val error = PipelineError(
                        message = stepResult.error,
                        stepIndex = index,
                        assistantId = assistantId
                    )

                    val status = if (steps.isNotEmpty()) {
                        PipelineExecutionStatus.PARTIAL
                    } else {
                        PipelineExecutionStatus.FAILED
                    }

                    logger.error("Step $index failed: ${stepResult.error}")

                    return currentExecution.copy(
                        steps = steps,
                        status = status,
                        endTime = System.currentTimeMillis(),
                        error = error
                    )
                }
            }
        }

        // All steps completed successfully
        return currentExecution.copy(
            steps = steps,
            status = PipelineExecutionStatus.COMPLETED,
            endTime = System.currentTimeMillis()
        )
    }

    /**
     * Выполнить один шаг pipeline
     */
    private suspend fun executeStep(
        assistantId: String,
        input: String,
        stepIndex: Int,
        execution: PipelineExecution,
        request: ExecutePipelineRequest
    ): StepResult {
        val stepStartTime = System.currentTimeMillis()

        return try {
            // Get assistant
            val assistant = assistantManager.getAssistant(assistantId)
                ?: return StepResult.Failure(
                    step = createErrorStep(assistantId, input, stepIndex, "Assistant not found"),
                    error = "Assistant '$assistantId' not found"
                )

            logger.info("🔹 Executing step $stepIndex: ${assistant.name} (ID: $assistantId)")
            logger.info("   System prompt preview: ${assistant.systemPrompt.take(100)}...")

            // Update session with current assistant ID to apply system prompt
            val session = sessionRepository.getSession(execution.sessionId).getOrThrow()
            logger.info("   Session before update - assistantId: ${session.assistantId}")

            val updatedSession = session.copy(assistantId = assistantId)
            sessionRepository.updateSession(updatedSession).getOrThrow()

            logger.info("   Session after update - assistantId: $assistantId ✅")

            // Execute via SendMessageUseCase
            val result = sendMessageUseCase(
                message = input,
                sessionId = execution.sessionId,
                providerId = request.providerId,
                model = request.model,
                parameters = request.parameters
            ).getOrElse { error ->
                return StepResult.Failure(
                    step = createErrorStep(assistantId, input, stepIndex, error.message ?: "Unknown error"),
                    error = error.message ?: "Unknown error"
                )
            }

            val executionTime = System.currentTimeMillis() - stepStartTime

            val step = AssistantStep(
                stepIndex = stepIndex,
                assistantId = assistantId,
                assistantName = assistant.name,
                input = input,
                output = result.response,
                tokensUsed = result.usage,
                executionTimeMs = executionTime,
                timestamp = System.currentTimeMillis()
            )

            StepResult.Success(step)
        } catch (e: Exception) {
            logger.error("Step $stepIndex execution failed", e)
            StepResult.Failure(
                step = createErrorStep(assistantId, input, stepIndex, e.message ?: "Unknown error"),
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Создать шаг с ошибкой
     */
    private fun createErrorStep(
        assistantId: String,
        input: String,
        stepIndex: Int,
        errorMessage: String
    ): AssistantStep {
        val assistant = assistantManager.getAssistant(assistantId)
        return AssistantStep(
            stepIndex = stepIndex,
            assistantId = assistantId,
            assistantName = assistant?.name ?: assistantId,
            input = input,
            output = "",
            tokensUsed = TokenUsage(0, 0, 0),
            executionTimeMs = 0L,
            timestamp = System.currentTimeMillis(),
            error = PipelineError(errorMessage, stepIndex, assistantId)
        )
    }

    /**
     * Resolve pipeline configuration
     */
    private suspend fun resolvePipelineConfig(
        request: ExecutePipelineRequest
    ): Result<Triple<List<String>, String?, String>> {
        return try {
            if (request.pipelineId != null) {
                // Load from storage
                val pipeline = pipelineStorage.loadPipeline(request.pipelineId).getOrThrow()
                    ?: return Result.failure(AIError.ConfigurationException("Pipeline not found: ${request.pipelineId}"))

                Result.success(Triple(pipeline.assistantIds, pipeline.id, pipeline.name))
            } else if (request.assistantIds != null) {
                // Use provided list
                val name = request.pipelineName ?: "Ad-hoc Pipeline"
                Result.success(Triple(request.assistantIds, null, name))
            } else {
                Result.failure(AIError.ConfigurationException("Either pipelineId or assistantIds must be provided"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validate assistants exist
     */
    private fun validateAssistants(assistantIds: List<String>): Result<Unit> {
        for (assistantId in assistantIds) {
            if (!assistantManager.hasAssistant(assistantId)) {
                return Result.failure(
                    AIError.ConfigurationException("Assistant not found: $assistantId")
                )
            }
        }
        return Result.success(Unit)
    }

    /**
     * Save pipeline configuration
     */
    private suspend fun savePipelineConfig(
        request: ExecutePipelineRequest,
        assistantIds: List<String>
    ): Result<Unit> {
        val pipeline = AssistantPipeline(
            id = UUID.randomUUID().toString(),
            name = request.pipelineName!!,
            description = request.pipelineDescription ?: "",
            assistantIds = assistantIds,
            providerId = request.providerId,
            model = request.model,
            defaultParameters = request.parameters
        )
        return pipelineStorage.savePipeline(pipeline)
    }

    /**
     * Get default model for provider
     */
    private fun getDefaultModel(providerId: ProviderType): String {
        return when (providerId) {
            ProviderType.CLAUDE -> "claude-sonnet-4-5-20250929"
            ProviderType.OPENAI -> "gpt-4-turbo"
            ProviderType.HUGGINGFACE -> "deepseek-ai/DeepSeek-R1:fastest"
            else -> "default"
        }
    }

    /**
     * Result of executing a step
     */
    private sealed class StepResult {
        data class Success(val step: AssistantStep) : StepResult()
        data class Failure(val step: AssistantStep, val error: String) : StepResult()
    }
}
