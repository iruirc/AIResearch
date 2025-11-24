package com.researchai.data.rag

import com.researchai.domain.models.RAGConfig
import com.researchai.domain.rag.EmbeddingService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class OllamaEmbeddingService(
    private val httpClient: HttpClient,
    private val config: RAGConfig
) : EmbeddingService {

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
                throw Exception("Ollama API returned status ${response.status}")
            }
        } catch (e: Exception) {
            throw Exception("Failed to generate embedding: ${e.message}", e)
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        return texts.map { text -> generateEmbedding(text) }
    }
}
