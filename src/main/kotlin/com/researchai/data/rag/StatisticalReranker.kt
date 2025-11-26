package com.researchai.data.rag

import com.researchai.domain.models.*
import com.researchai.domain.rag.Reranker
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Statistical reranker - filters outliers based on score distribution
 *
 * Uses mean and standard deviation to determine a dynamic threshold.
 * Results with score < (mean - stdDevMultiplier * stdDev) are filtered out.
 */
class StatisticalReranker : Reranker {

    override suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult {
        if (results.isEmpty()) {
            return createEmptyResult(results)
        }

        var filteredResults: List<SearchResult>
        var stdDev: Float
        var threshold: Float
        val processingTime = measureTimeMillis {
            val scores = results.map { it.score }
            val mean = scores.average().toFloat()
            stdDev = calculateStdDev(scores, mean)

            // Dynamic threshold: mean - (multiplier * stdDev)
            threshold = mean - (config.stdDevMultiplier * stdDev)

            // Filter results below threshold
            filteredResults = results.filter { it.score >= threshold }

            // Ensure minimum results are kept
            if (filteredResults.size < config.minResultsToKeep && results.isNotEmpty()) {
                filteredResults = results
                    .sortedByDescending { it.score }
                    .take(config.minResultsToKeep)
            }
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
            strategy = RerankerStrategy.STATISTICAL,
            statistics = RerankerStatistics(
                inputCount = results.size,
                outputCount = filteredResults.size,
                filteredCount = results.size - filteredResults.size,
                avgScoreBefore = avgBefore,
                avgScoreAfter = avgAfter,
                scoreStdDev = stdDev,
                thresholdUsed = threshold,
                processingTimeMs = processingTime
            )
        )
    }

    private fun calculateStdDev(scores: List<Float>, mean: Float): Float {
        if (scores.size <= 1) return 0f

        val squaredDiffs = scores.map { (it - mean) * (it - mean) }
        val variance = squaredDiffs.sum() / scores.size
        return sqrt(variance)
    }

    private fun createEmptyResult(results: List<SearchResult>): RerankerResult {
        return RerankerResult(
            results = emptyList(),
            originalResults = results,
            strategy = RerankerStrategy.STATISTICAL,
            statistics = RerankerStatistics(
                inputCount = 0,
                outputCount = 0,
                filteredCount = 0,
                avgScoreBefore = 0f,
                avgScoreAfter = 0f,
                scoreStdDev = 0f,
                thresholdUsed = 0f,
                processingTimeMs = 0
            )
        )
    }
}
