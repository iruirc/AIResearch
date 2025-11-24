package com.researchai.domain.rag

interface EmbeddingService {
    suspend fun generateEmbedding(text: String): List<Float>
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>>
}
