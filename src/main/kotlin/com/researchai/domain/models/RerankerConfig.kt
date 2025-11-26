package com.researchai.domain.models

import kotlinx.serialization.Serializable

/**
 * Strategy for second-stage reranking/filtering of search results
 */
@Serializable
enum class RerankerStrategy {
    /**
     * No reranking - use first-stage results as-is
     */
    NONE,

    /**
     * Score threshold filter - removes results below secondary threshold
     */
    SCORE_THRESHOLD,

    /**
     * Statistical filter - removes outliers based on score distribution
     */
    STATISTICAL,

    /**
     * Cross-encoder reranker - uses LLM to rerank results based on query relevance
     */
    CROSS_ENCODER
}

/**
 * Configuration for reranker behavior
 */
@Serializable
data class RerankerConfig(
    /**
     * Reranking strategy to use
     */
    val strategy: RerankerStrategy = RerankerStrategy.NONE,

    /**
     * Secondary score threshold for SCORE_THRESHOLD strategy
     * Results with score below this value are filtered out after first stage
     * Should be >= searchMinScore from RAGConfig
     */
    val secondaryThreshold: Float = 0.75f,

    /**
     * For STATISTICAL strategy: number of standard deviations below mean to keep
     * Results with score < (mean - stdDevMultiplier * stdDev) are filtered
     */
    val stdDevMultiplier: Float = 1.0f,

    /**
     * For STATISTICAL strategy: minimum number of results to keep regardless of stats
     */
    val minResultsToKeep: Int = 1,

    /**
     * For CROSS_ENCODER strategy: Ollama model to use for reranking
     */
    val crossEncoderModel: String = "llama3.2:latest",

    /**
     * For CROSS_ENCODER strategy: minimum relevance score from LLM (0-10 scale)
     */
    val crossEncoderMinScore: Float = 6.0f,

    /**
     * Whether to include reranking metadata in results
     */
    val includeMetadata: Boolean = true
)

/**
 * Result of reranking operation with detailed statistics
 */
@Serializable
data class RerankerResult(
    /**
     * Results after reranking/filtering
     */
    val results: List<SearchResult>,

    /**
     * Original results before reranking (for comparison)
     */
    val originalResults: List<SearchResult>,

    /**
     * Strategy used for reranking
     */
    val strategy: RerankerStrategy,

    /**
     * Statistics about the reranking operation
     */
    val statistics: RerankerStatistics
)

/**
 * Statistics from reranking operation
 */
@Serializable
data class RerankerStatistics(
    /**
     * Number of results before reranking
     */
    val inputCount: Int,

    /**
     * Number of results after reranking
     */
    val outputCount: Int,

    /**
     * Number of results filtered out
     */
    val filteredCount: Int,

    /**
     * Average score before reranking
     */
    val avgScoreBefore: Float,

    /**
     * Average score after reranking
     */
    val avgScoreAfter: Float,

    /**
     * Score standard deviation (for STATISTICAL strategy)
     */
    val scoreStdDev: Float? = null,

    /**
     * Threshold used for filtering
     */
    val thresholdUsed: Float? = null,

    /**
     * Processing time in milliseconds
     */
    val processingTimeMs: Long
)
