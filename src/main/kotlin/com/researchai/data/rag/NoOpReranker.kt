package com.researchai.data.rag

import com.researchai.domain.models.*
import com.researchai.domain.rag.Reranker
import kotlin.system.measureTimeMillis

/**
 * No-operation reranker - returns results as-is without modification
 */
class NoOpReranker : Reranker {

    override suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult {
        val avgScore = if (results.isNotEmpty()) {
            results.map { it.score }.average().toFloat()
        } else {
            0f
        }

        return RerankerResult(
            results = results,
            originalResults = results,
            strategy = RerankerStrategy.NONE,
            statistics = RerankerStatistics(
                inputCount = results.size,
                outputCount = results.size,
                filteredCount = 0,
                avgScoreBefore = avgScore,
                avgScoreAfter = avgScore,
                processingTimeMs = 0
            )
        )
    }
}
