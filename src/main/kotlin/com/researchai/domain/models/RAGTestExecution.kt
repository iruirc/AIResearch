package com.researchai.domain.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Result of a single query execution during RAG test.
 */
@Serializable
data class QueryExecutionResult(
    val queryId: String,
    val query: String,
    val explanation: String,
    val response: String,
    val elapsedTimeMs: Long,
    val tokensUsed: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val model: String? = null
)

/**
 * Complete result of RAG test execution.
 */
@Serializable
data class TestExecutionResult(
    val testId: String,
    val testName: String,
    val sessionId: String,
    val results: List<QueryExecutionResult>,
    val totalTimeMs: Long,
    val executedAt: Instant,
    val provider: String,
    val model: String,
    val cancelled: Boolean = false
)

/**
 * SSE event types for test execution progress.
 */
@Serializable
sealed class TestExecutionEvent {
    abstract val type: String
}

/**
 * Event sent when execution starts.
 */
@Serializable
data class ExecutionStartedEvent(
    override val type: String = "started",
    val testId: String,
    val testName: String,
    val sessionId: String,
    val totalQueries: Int
) : TestExecutionEvent()

/**
 * Event sent when starting to process a query.
 */
@Serializable
data class QueryProcessingEvent(
    override val type: String = "processing",
    val current: Int,
    val total: Int,
    val queryId: String,
    val query: String
) : TestExecutionEvent()

/**
 * Event sent when a query execution completes.
 */
@Serializable
data class QueryCompletedEvent(
    override val type: String = "completed",
    val current: Int,
    val total: Int,
    val result: QueryExecutionResult
) : TestExecutionEvent()

/**
 * Event sent when an error occurs during query execution.
 */
@Serializable
data class QueryErrorEvent(
    override val type: String = "error",
    val current: Int,
    val total: Int,
    val queryId: String,
    val query: String,
    val error: String
) : TestExecutionEvent()

/**
 * Event sent when entire test execution completes.
 */
@Serializable
data class ExecutionFinishedEvent(
    override val type: String = "finished",
    val result: TestExecutionResult
) : TestExecutionEvent()

/**
 * Event sent when execution is cancelled.
 */
@Serializable
data class ExecutionCancelledEvent(
    override val type: String = "cancelled",
    val result: TestExecutionResult
) : TestExecutionEvent()
