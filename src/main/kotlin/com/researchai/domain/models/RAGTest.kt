package com.researchai.domain.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Model representing a RAG test case.
 * Tests are used to verify RAG search quality with predefined content.
 */
@Serializable
data class RAGTest(
    val id: String,
    val name: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
