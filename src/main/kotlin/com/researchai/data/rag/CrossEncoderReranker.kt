package com.researchai.data.rag

import com.researchai.domain.models.*
import com.researchai.domain.rag.Reranker
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * Cross-encoder reranker using Ollama LLM to evaluate relevance
 *
 * Sends query + document pairs to LLM and asks for relevance score.
 * More accurate but slower than embedding-based methods.
 */
class CrossEncoderReranker(
    private val httpClient: HttpClient,
    private val ollamaUrl: String
) : Reranker {

    private val logger = LoggerFactory.getLogger(CrossEncoderReranker::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val DELAY_BETWEEN_REQUESTS_MS = 100L
    }

    @Serializable
    private data class OllamaGenerateRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false,
        val options: OllamaOptions = OllamaOptions()
    )

    @Serializable
    private data class OllamaOptions(
        val temperature: Float = 0.0f,
        val num_predict: Int = 10
    )

    @Serializable
    private data class OllamaGenerateResponse(
        val response: String
    )

    override suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult {
        if (results.isEmpty()) {
            return createEmptyResult(results, config)
        }

        val scoredResults = mutableListOf<Pair<SearchResult, Float>>()
        val processingTime = measureTimeMillis {
            for ((index, result) in results.withIndex()) {
                if (index > 0) {
                    delay(DELAY_BETWEEN_REQUESTS_MS)
                }

                try {
                    val llmScore = evaluateRelevance(query, result.text, config.crossEncoderModel)
                    scoredResults.add(result to llmScore)
                    logger.debug("LLM score for chunk ${result.chunkIndex}: $llmScore")
                } catch (e: Exception) {
                    logger.warn("Failed to evaluate relevance for chunk ${result.chunkIndex}: ${e.message}")
                    // Keep original score if LLM evaluation fails
                    scoredResults.add(result to result.score * 10) // Scale to 0-10
                }
            }
        }

        // Filter by minimum LLM score and sort by it
        val filteredResults = scoredResults
            .filter { (_, llmScore) -> llmScore >= config.crossEncoderMinScore }
            .sortedByDescending { (_, llmScore) -> llmScore }
            .map { (result, llmScore) ->
                // Update score with normalized LLM score (0-1 range)
                result.copy(score = llmScore / 10f)
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
            strategy = RerankerStrategy.CROSS_ENCODER,
            statistics = RerankerStatistics(
                inputCount = results.size,
                outputCount = filteredResults.size,
                filteredCount = results.size - filteredResults.size,
                avgScoreBefore = avgBefore,
                avgScoreAfter = avgAfter,
                thresholdUsed = config.crossEncoderMinScore / 10f, // Normalized threshold
                processingTimeMs = processingTime
            )
        )
    }

    /**
     * Ask LLM to evaluate relevance of document to query on scale 0-10
     */
    private suspend fun evaluateRelevance(query: String, documentText: String, model: String): Float {
        val prompt = buildRelevancePrompt(query, documentText)

        val response = httpClient.post("$ollamaUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false
            ))
        }

        if (!response.status.isSuccess()) {
            throw Exception("Ollama API returned status ${response.status}")
        }

        // Read response as text first to handle x-ndjson content type
        val responseText = response.bodyAsText()
        val generateResponse = json.decodeFromString<OllamaGenerateResponse>(responseText)
        return parseScoreFromResponse(generateResponse.response)
    }

    private fun buildRelevancePrompt(query: String, documentText: String): String {
        return """You are a relevance evaluator. Rate how relevant the given document is to the query.

Query: $query

Document:
${documentText.take(1500)}

Rate the relevance from 0 to 10, where:
- 0 = completely irrelevant
- 5 = somewhat relevant
- 10 = highly relevant and directly answers the query

IMPORTANT: Respond with ONLY a single number from 0 to 10. No explanations.

Score:"""
    }

    private fun parseScoreFromResponse(response: String): Float {
        // Extract first number from response
        val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
        val match = numberRegex.find(response.trim())

        return match?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 10f)
            ?: 5f // Default to middle score if parsing fails
    }

    private fun createEmptyResult(results: List<SearchResult>, config: RerankerConfig): RerankerResult {
        return RerankerResult(
            results = emptyList(),
            originalResults = results,
            strategy = RerankerStrategy.CROSS_ENCODER,
            statistics = RerankerStatistics(
                inputCount = 0,
                outputCount = 0,
                filteredCount = 0,
                avgScoreBefore = 0f,
                avgScoreAfter = 0f,
                thresholdUsed = config.crossEncoderMinScore / 10f,
                processingTimeMs = 0
            )
        )
    }
}
