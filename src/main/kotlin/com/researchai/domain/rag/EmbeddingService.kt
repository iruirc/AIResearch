package com.researchai.domain.rag

typealias EmbeddingProgressCallback = suspend (current: Int, total: Int) -> Unit

interface EmbeddingService {
    suspend fun generateEmbedding(text: String): List<Float>
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>>

    /**
     * Generate embeddings with progress callback
     * @param texts List of texts to generate embeddings for
     * @param onProgress Callback invoked after each embedding is generated (current, total)
     */
    suspend fun generateEmbeddings(
        texts: List<String>,
        onProgress: EmbeddingProgressCallback?
    ): List<List<Float>>
}
