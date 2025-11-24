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
    val score: Float
)
