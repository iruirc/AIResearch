package com.researchai.data.rag

import com.researchai.domain.models.*
import com.researchai.domain.rag.Reranker
import kotlin.system.measureTimeMillis

/**
 * Simple score threshold reranker - filters results below secondary threshold
 */
class ScoreThresholdReranker : Reranker {

    override suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult {
        if (results.isEmpty()) {
            return createEmptyResult(results, config)
        }

        var filteredResults: List<SearchResult>
        val processingTime = measureTimeMillis {
            filteredResults = results.filter { it.score >= config.secondaryThreshold }
        }

        val avgBefore = results.map { it.score }.average().toFloat()
        val avgAfter = if (filteredResults.isNotEmpty()) {
            filteredResults.map { it.score }.average().toFloat()
        } else {
            0f
        }

        return RerankerResult(
            results = filteredResults,
            originalResults = results,
            strategy = RerankerStrategy.SCORE_THRESHOLD,
            statistics = RerankerStatistics(
                inputCount = results.size,
                outputCount = filteredResults.size,
                filteredCount = results.size - filteredResults.size,
                avgScoreBefore = avgBefore,
                avgScoreAfter = avgAfter,
                thresholdUsed = config.secondaryThreshold,
                processingTimeMs = processingTime
            )
        )
    }

    private fun createEmptyResult(results: List<SearchResult>, config: RerankerConfig): RerankerResult {
        return RerankerResult(
            results = emptyList(),
            originalResults = results,
            strategy = RerankerStrategy.SCORE_THRESHOLD,
            statistics = RerankerStatistics(
                inputCount = 0,
                outputCount = 0,
                filteredCount = 0,
                avgScoreBefore = 0f,
                avgScoreAfter = 0f,
                thresholdUsed = config.secondaryThreshold,
                processingTimeMs = 0
            )
        )
    }
}
