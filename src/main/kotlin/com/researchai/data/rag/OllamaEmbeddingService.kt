package com.researchai.data.rag

import com.researchai.domain.models.RAGConfig
import com.researchai.domain.rag.EmbeddingProgressCallback
import com.researchai.domain.rag.EmbeddingService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class OllamaEmbeddingService(
    private val httpClient: HttpClient,
    private val config: RAGConfig
) : EmbeddingService {

    private val logger = LoggerFactory.getLogger(OllamaEmbeddingService::class.java)

    companion object {
        private const val MAX_RETRIES = 5
        private const val INITIAL_DELAY_MS = 1000L
        private const val DELAY_BETWEEN_REQUESTS_MS = 200L
    }

    @Serializable
    private data class OllamaEmbeddingRequest(
        val model: String,
        val prompt: String
    )

    @Serializable
    private data class OllamaEmbeddingResponse(
        val embedding: List<Float>
    )

    override suspend fun generateEmbedding(text: String): List<Float> {
        return generateEmbeddingWithRetry(text)
    }

    private suspend fun generateEmbeddingWithRetry(text: String, attempt: Int = 1): List<Float> {
        return try {
            val response = httpClient.post("${config.ollamaUrl}/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(OllamaEmbeddingRequest(
                    model = config.embeddingModel,
                    prompt = text
                ))
            }

            if (response.status.isSuccess()) {
                val embeddingResponse = response.body<OllamaEmbeddingResponse>()
                embeddingResponse.embedding
            } else {
                val statusCode = response.status.value
                val errorBody = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    "Unable to read error body"
                }

                if (statusCode >= 500 && attempt < MAX_RETRIES) {
                    // Server error - retry with exponential backoff
                    val delayMs = INITIAL_DELAY_MS * (1 shl (attempt - 1)) // exponential backoff
                    logger.warn("Ollama API returned $statusCode, retrying in ${delayMs}ms (attempt $attempt/$MAX_RETRIES). Model: ${config.embeddingModel}, Error: $errorBody")
                    delay(delayMs)
                    generateEmbeddingWithRetry(text, attempt + 1)
                } else {
                    logger.error("Ollama embedding API failed. Model: ${config.embeddingModel}, Status: ${response.status}, Error: $errorBody")
                    throw Exception("Ollama API returned status ${response.status}: $errorBody")
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("Ollama API returned status") == true) {
                throw e // Already our exception, don't wrap
            }
            if (attempt < MAX_RETRIES) {
                val delayMs = INITIAL_DELAY_MS * (1 shl (attempt - 1))
                logger.warn("Embedding request failed: ${e.message}, retrying in ${delayMs}ms (attempt $attempt/$MAX_RETRIES)")
                delay(delayMs)
                generateEmbeddingWithRetry(text, attempt + 1)
            } else {
                throw Exception("Failed to generate embedding: ${e.message}", e)
            }
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        return generateEmbeddings(texts, null)
    }

    override suspend fun generateEmbeddings(
        texts: List<String>,
        onProgress: EmbeddingProgressCallback?
    ): List<List<Float>> {
        val results = mutableListOf<List<Float>>()
        val total = texts.size

        for ((index, text) in texts.withIndex()) {
            if (index > 0) {
                // Add small delay between requests to prevent overloading Ollama
                delay(DELAY_BETWEEN_REQUESTS_MS)
            }
            results.add(generateEmbedding(text))

            // Invoke progress callback after each embedding
            onProgress?.invoke(index + 1, total)
        }
        return results
    }
}
