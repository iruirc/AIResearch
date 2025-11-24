package com.researchai.data.rag

import com.researchai.domain.rag.TextChunker
import com.researchai.domain.rag.EmbeddingService

class SemanticTextChunker(
    private val embeddingService: EmbeddingService,
    private val similarityThreshold: Float = 0.75f,
    private val minChunkSize: Int = 200,
    private val maxChunkSize: Int = 1000
) : TextChunker {

    override fun chunk(text: String): List<String> {
        return FixedSizeTextChunker().chunk(text)
    }
}
