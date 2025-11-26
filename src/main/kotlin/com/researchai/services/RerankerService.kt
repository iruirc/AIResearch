package com.researchai.services

import com.researchai.data.rag.CrossEncoderReranker
import com.researchai.data.rag.NoOpReranker
import com.researchai.data.rag.ScoreThresholdReranker
import com.researchai.data.rag.StatisticalReranker
import com.researchai.domain.models.*
import com.researchai.domain.rag.Reranker
import io.ktor.client.*
import org.slf4j.LoggerFactory

/**
 * Service for managing and executing reranking operations
 */
class RerankerService(
    private val httpClient: HttpClient,
    private val ollamaUrl: String
) {
    private val logger = LoggerFactory.getLogger(RerankerService::class.java)

    private val noOpReranker = NoOpReranker()
    private val scoreThresholdReranker = ScoreThresholdReranker()
    private val statisticalReranker = StatisticalReranker()
    private val crossEncoderReranker = CrossEncoderReranker(httpClient, ollamaUrl)

    /**
     * Get reranker by strategy
     */
    fun getReranker(strategy: RerankerStrategy): Reranker {
        return when (strategy) {
            RerankerStrategy.NONE -> noOpReranker
            RerankerStrategy.SCORE_THRESHOLD -> scoreThresholdReranker
            RerankerStrategy.STATISTICAL -> statisticalReranker
            RerankerStrategy.CROSS_ENCODER -> crossEncoderReranker
        }
    }

    /**
     * Execute reranking with the configured strategy
     */
    suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult {
        logger.debug("Reranking ${results.size} results with strategy: ${config.strategy}")

        val reranker = getReranker(config.strategy)
        val result = reranker.rerank(query, results, config)

        logger.info(
            "Reranking complete: ${result.statistics.inputCount} -> ${result.statistics.outputCount} results " +
            "(filtered ${result.statistics.filteredCount}, avg score: ${String.format("%.3f", result.statistics.avgScoreBefore)} -> " +
            "${String.format("%.3f", result.statistics.avgScoreAfter)}, time: ${result.statistics.processingTimeMs}ms)"
        )

        return result
    }

    /**
     * Compare results with and without reranking
     */
    suspend fun compareWithAndWithoutReranking(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): ComparisonResult {
        // Get results without reranking
        val withoutReranking = noOpReranker.rerank(query, results, config)

        // Get results with reranking
        val withReranking = rerank(query, results, config)

        return ComparisonResult(
            query = query,
            withoutReranking = withoutReranking,
            withReranking = withReranking,
            comparison = buildComparison(withoutReranking, withReranking)
        )
    }

    private fun buildComparison(
        without: RerankerResult,
        with: RerankerResult
    ): ComparisonStatistics {
        val removedResults = without.results.filter { original ->
            with.results.none { it.documentId == original.documentId && it.chunkIndex == original.chunkIndex }
        }

        val keptResults = without.results.filter { original ->
            with.results.any { it.documentId == original.documentId && it.chunkIndex == original.chunkIndex }
        }

        val reorderedCount = if (with.results.isNotEmpty() && without.results.isNotEmpty()) {
            // Count how many results changed position
            with.results.mapIndexed { newIndex, result ->
                val oldIndex = without.results.indexOfFirst {
                    it.documentId == result.documentId && it.chunkIndex == result.chunkIndex
                }
                if (oldIndex != -1 && oldIndex != newIndex) 1 else 0
            }.sum()
        } else {
            0
        }

        return ComparisonStatistics(
            removedCount = removedResults.size,
            removedResults = removedResults,
            keptCount = keptResults.size,
            reorderedCount = reorderedCount,
            qualityImprovement = with.statistics.avgScoreAfter - without.statistics.avgScoreAfter,
            processingOverheadMs = with.statistics.processingTimeMs
        )
    }
}

/**
 * Result of comparing search with and without reranking
 */
@kotlinx.serialization.Serializable
data class ComparisonResult(
    val query: String,
    val withoutReranking: RerankerResult,
    val withReranking: RerankerResult,
    val comparison: ComparisonStatistics
)

/**
 * Statistics comparing reranked vs non-reranked results
 */
@kotlinx.serialization.Serializable
data class ComparisonStatistics(
    /**
     * Number of results removed by reranking
     */
    val removedCount: Int,

    /**
     * Results that were removed by reranking
     */
    val removedResults: List<SearchResult>,

    /**
     * Number of results kept after reranking
     */
    val keptCount: Int,

    /**
     * Number of results that changed position
     */
    val reorderedCount: Int,

    /**
     * Improvement in average score (positive = better)
     */
    val qualityImprovement: Float,

    /**
     * Additional processing time for reranking (ms)
     */
    val processingOverheadMs: Long
)
