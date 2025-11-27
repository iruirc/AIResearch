package com.researchai.services

import com.researchai.domain.models.*
import com.researchai.domain.usecase.MessageResult
import com.researchai.domain.usecase.SendMessageUseCase
import com.researchai.models.UserPreferences
import com.researchai.persistence.RAGTestStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for executing RAG tests with batch query processing.
 * Supports real-time progress streaming via Flow and cancellation.
 * Each query is executed twice: without reranking and with reranking.
 */
class RAGTestExecutionService(
    private val testStorage: RAGTestStorage,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sessionManager: ChatSessionManager,
    private val preferencesManager: PreferencesManager
) {
    private val logger = LoggerFactory.getLogger(RAGTestExecutionService::class.java)

    // Active executions for cancellation support
    private val activeExecutions = ConcurrentHashMap<String, Job>()

    /**
     * Execute a RAG test and stream progress events.
     * Each query is executed twice: without reranking and with reranking.
     *
     * @param testId ID of the test to execute
     * @return Flow of TestExecutionEvent for real-time progress updates
     */
    fun executeTest(testId: String): Flow<TestExecutionEvent> = flow {
        val executionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        // Load test
        val test = testStorage.load(testId)
            ?: throw IllegalArgumentException("Test not found: $testId")

        logger.info("Starting RAG test execution: ${test.name} (${test.queries.size} queries, dual mode: without/with reranking)")

        // Get global preferences for provider/model
        val preferences = preferencesManager.getPreferences()
        val providerId = parseProviderType(preferences.providerId)
        val model = preferences.model

        // Create two separate sessions for test execution (to avoid history accumulation)
        val timestamp = System.currentTimeMillis()
        val sessionIdWithoutReranking = "rag-test-${test.id}-no-rerank-$timestamp"
        val sessionIdWithReranking = "rag-test-${test.id}-rerank-$timestamp"

        val (actualSessionIdNoRerank, _) = sessionManager.getOrCreateSession(sessionIdWithoutReranking)
        val (actualSessionIdRerank, _) = sessionManager.getOrCreateSession(sessionIdWithReranking)

        logger.info("Using sessions: noRerank=$actualSessionIdNoRerank, rerank=$actualSessionIdRerank, provider: $providerId, model: $model")

        // Emit started event
        emit(ExecutionStartedEvent(
            testId = test.id,
            testName = test.name,
            sessionId = actualSessionIdNoRerank, // Primary session ID for reference
            totalQueries = test.queries.size
        ))

        val results = mutableListOf<QueryExecutionResult>()
        var cancelled = false

        // Process each query sequentially
        for ((index, query) in test.queries.withIndex()) {
            // Check for cancellation
            currentCoroutineContext().ensureActive()

            val current = index + 1
            val total = test.queries.size

            // Emit processing event
            emit(QueryProcessingEvent(
                current = current,
                total = total,
                queryId = query.id,
                query = query.query
            ))

            try {
                // Execute query WITHOUT reranking
                logger.info("Query $current/$total: executing WITHOUT reranking")
                val withoutRerankingStartTime = System.currentTimeMillis()
                val resultWithoutReranking = sendMessageUseCase(
                    message = query.query,
                    sessionId = actualSessionIdNoRerank,
                    providerId = providerId,
                    model = model,
                    parameters = RequestParameters(
                        temperature = preferences.temperature,
                        maxTokens = preferences.maxTokens
                    ),
                    useRerankingOverride = false
                )

                // Check for cancellation between requests
                currentCoroutineContext().ensureActive()

                // Execute query WITH reranking
                logger.info("Query $current/$total: executing WITH reranking")
                val withRerankingStartTime = System.currentTimeMillis()
                val resultWithReranking = sendMessageUseCase(
                    message = query.query,
                    sessionId = actualSessionIdRerank,
                    providerId = providerId,
                    model = model,
                    parameters = RequestParameters(
                        temperature = preferences.temperature,
                        maxTokens = preferences.maxTokens
                    ),
                    useRerankingOverride = true
                )

                // Build QueryExecutionResult from both results
                val withoutRerankingData = buildQueryResponseData(
                    resultWithoutReranking,
                    System.currentTimeMillis() - withoutRerankingStartTime
                )
                val withRerankingData = buildQueryResponseData(
                    resultWithReranking,
                    System.currentTimeMillis() - withRerankingStartTime
                )

                val queryResult = QueryExecutionResult(
                    queryId = query.id,
                    query = query.query,
                    explanation = query.explanation,
                    model = resultWithoutReranking.getOrNull()?.model ?: model,
                    withoutReranking = withoutRerankingData,
                    withReranking = withRerankingData
                )
                results.add(queryResult)

                // Emit completed event
                emit(QueryCompletedEvent(
                    current = current,
                    total = total,
                    result = queryResult
                ))

                logger.info("Query $current/$total completed: ${query.id} (noRerank: ${withoutRerankingData.chunksCount} chunks, rerank: ${withRerankingData.chunksCount} chunks)")

            } catch (e: CancellationException) {
                logger.info("Test execution cancelled at query $current/$total")
                cancelled = true
                throw e
            } catch (e: Exception) {
                logger.error("Unexpected error during query execution", e)

                // Create error result with empty response data
                val errorData = QueryResponseData(
                    response = "ERROR: ${e.message}",
                    elapsedTimeMs = 0,
                    chunksCount = 0
                )
                val errorResult = QueryExecutionResult(
                    queryId = query.id,
                    query = query.query,
                    explanation = query.explanation,
                    model = model,
                    withoutReranking = errorData,
                    withReranking = errorData
                )
                results.add(errorResult)

                emit(QueryErrorEvent(
                    current = current,
                    total = total,
                    queryId = query.id,
                    query = query.query,
                    error = e.message ?: "Unknown error"
                ))
            }
        }

        val totalTime = System.currentTimeMillis() - startTime

        // Build final result
        val executionResult = TestExecutionResult(
            testId = test.id,
            testName = test.name,
            sessionId = actualSessionIdNoRerank,
            results = results,
            totalTimeMs = totalTime,
            executedAt = Clock.System.now(),
            provider = providerId.name,
            model = model,
            cancelled = cancelled
        )

        // Emit finished event
        if (cancelled) {
            emit(ExecutionCancelledEvent(result = executionResult))
        } else {
            emit(ExecutionFinishedEvent(result = executionResult))
        }

        logger.info("RAG test execution completed: ${test.name}, total time: ${totalTime}ms, results: ${results.size}")

    }.catch { e ->
        if (e is CancellationException) {
            // Re-throw cancellation to properly handle it
            throw e
        }
        logger.error("Error during test execution", e)
        throw e
    }

    /**
     * Build QueryResponseData from SendMessageUseCase result.
     */
    private fun buildQueryResponseData(
        result: Result<MessageResult>,
        elapsedTimeMs: Long
    ): QueryResponseData {
        return result.fold(
            onSuccess = { messageResult ->
                val chunks = messageResult.ragDebugInfo?.usedResults?.map { searchResult ->
                    ChunkInfo(
                        documentName = searchResult.documentName,
                        chunkIndex = searchResult.chunkIndex,
                        score = searchResult.rerankedScore ?: searchResult.score,
                        text = searchResult.text
                    )
                }

                QueryResponseData(
                    response = messageResult.response,
                    elapsedTimeMs = elapsedTimeMs,
                    tokensUsed = messageResult.usage.totalTokens,
                    inputTokens = messageResult.usage.inputTokens,
                    outputTokens = messageResult.usage.outputTokens,
                    chunksCount = chunks?.size ?: 0,
                    chunks = chunks
                )
            },
            onFailure = { error ->
                QueryResponseData(
                    response = "ERROR: ${error.message}",
                    elapsedTimeMs = elapsedTimeMs,
                    chunksCount = 0
                )
            }
        )
    }

    /**
     * Start test execution and track it for cancellation.
     *
     * @param testId ID of the test to execute
     * @param scope CoroutineScope to launch the execution in
     * @return Pair of executionId and Flow of events
     */
    fun startExecution(testId: String, scope: CoroutineScope): Pair<String, Flow<TestExecutionEvent>> {
        val executionId = UUID.randomUUID().toString()
        val flow = executeTest(testId)

        // The job will be tracked when the flow is collected
        return executionId to flow
    }

    /**
     * Register an active execution for cancellation support.
     */
    fun registerExecution(executionId: String, job: Job) {
        activeExecutions[executionId] = job
        job.invokeOnCompletion {
            activeExecutions.remove(executionId)
        }
    }

    /**
     * Cancel an active test execution.
     *
     * @param executionId ID of the execution to cancel
     * @return true if cancellation was initiated, false if execution not found
     */
    fun cancelExecution(executionId: String): Boolean {
        val job = activeExecutions[executionId]
        return if (job != null && job.isActive) {
            logger.info("Cancelling test execution: $executionId")
            job.cancel()
            true
        } else {
            logger.warn("Execution not found or already completed: $executionId")
            false
        }
    }

    /**
     * Check if an execution is active.
     */
    fun isExecutionActive(executionId: String): Boolean {
        return activeExecutions[executionId]?.isActive == true
    }

    /**
     * Parse provider type from string.
     */
    private fun parseProviderType(providerId: String): ProviderType {
        return when (providerId.lowercase()) {
            "claude" -> ProviderType.CLAUDE
            "openai" -> ProviderType.OPENAI
            "huggingface" -> ProviderType.HUGGINGFACE
            "ollama" -> ProviderType.OLLAMA
            else -> ProviderType.CLAUDE
        }
    }
}
