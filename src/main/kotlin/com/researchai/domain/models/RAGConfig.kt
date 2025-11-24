package com.researchai.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class RAGConfig(
    val ollamaUrl: String = "http://127.0.0.1:11434",
    val embeddingModel: String = "nomic-embed-text",
    val defaultChunkSize: Int = 800,
    val defaultOverlap: Int = 100,
    val searchTopK: Int = 5,
    val searchMinScore: Float = 0.7f,
    val enabledByDefault: Boolean = true
)
