package com.researchai.domain.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class RAGDocument(
    val id: String,
    val name: String,
    val content: String,
    val chunks: List<DocumentChunk>,
    val chunkingStrategy: ChunkingStrategy,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class DocumentChunk(
    val text: String,
    val embedding: List<Float>,
    val chunkIndex: Int,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class SearchResult(
    val documentId: String,
    val documentName: String,
    val chunkIndex: Int,
    val text: String,
    val score: Float,
    /**
     * Score after reranking (if applicable)
     */
    val rerankedScore: Float? = null
)

/**
 * Debug information about RAG context used in a chat response
 */
@Serializable
data class RAGDebugInfo(
    /**
     * Query used for search
     */
    val query: String,

    /**
     * Results used in the response (after filtering)
     */
    val usedResults: List<SearchResult>,

    /**
     * Results that were filtered out by reranking
     */
    val filteredResults: List<SearchResult> = emptyList(),

    /**
     * Whether reranking was enabled
     */
    val rerankingEnabled: Boolean,

    /**
     * Reranking strategy used (if enabled)
     */
    val rerankingStrategy: String? = null,

    /**
     * Total processing time in milliseconds
     */
    val processingTimeMs: Long = 0,

    /**
     * Token count estimate for the context
     */
    val estimatedTokens: Int = 0
)
