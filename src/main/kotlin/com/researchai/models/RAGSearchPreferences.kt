package com.researchai.models

import com.researchai.domain.models.RerankerStrategy
import kotlinx.serialization.Serializable

/**
 * User preferences for RAG search configuration
 * Includes reranking settings and search parameters
 */
@Serializable
data class RAGSearchPreferences(
    /**
     * Whether to use reranking after vector search
     */
    val enableReranking: Boolean = false,

    /**
     * Reranking strategy to use when reranking is enabled
     */
    val strategy: RerankerStrategy = RerankerStrategy.SCORE_THRESHOLD,

    // SCORE_THRESHOLD parameters
    /**
     * Secondary threshold for SCORE_THRESHOLD strategy
     * Results below this score are filtered out
     */
    val scoreThreshold: Float = 0.75f,

    // STATISTICAL parameters
    /**
     * Standard deviation multiplier for STATISTICAL strategy
     * Threshold = mean - (stdDevMultiplier * stdDev)
     */
    val stdDevMultiplier: Float = 1.0f,

    /**
     * Minimum number of results to keep regardless of threshold
     */
    val minResultsToKeep: Int = 1,

    // CROSS_ENCODER parameters
    /**
     * AI provider for Cross-Encoder reranking
     * If null, uses the current global provider
     */
    val crossEncoderProvider: String? = null,

    /**
     * Model to use for Cross-Encoder reranking
     * If null, uses the current global model
     */
    val crossEncoderModel: String? = null,

    /**
     * Minimum relevance score (0-10) for Cross-Encoder
     */
    val crossEncoderMinScore: Float = 6.0f,

    // General search parameters
    /**
     * Number of top results to retrieve in first stage
     */
    val searchTopK: Int = 5,

    /**
     * Minimum cosine similarity score for first stage search
     */
    val searchMinScore: Float = 0.7f,

    // Debug settings
    /**
     * Whether to include RAG debug info in chat responses
     * Shows which chunks were used and their scores
     */
    val debugMode: Boolean = false,

    /**
     * Timestamp of last update
     */
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Default preferences - reranking disabled
         */
        fun default() = RAGSearchPreferences()
    }
}
