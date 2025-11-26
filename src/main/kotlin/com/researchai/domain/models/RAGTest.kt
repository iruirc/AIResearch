package com.researchai.domain.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Model representing a single RAG test query.
 * Used to verify RAG search quality with predefined queries.
 */
@Serializable
data class RAGTestQuery(
    val id: String,
    val query: String,
    val explanation: String,
    // Optional fields
    val scenario: String? = null,
    val expectedTopResult: String? = null,
    val expectedChunkKeywords: List<String>? = null,
    val rankingTrap: String? = null
)

/**
 * Model representing a RAG test case.
 * Contains a collection of test queries for verifying RAG search quality.
 */
@Serializable
data class RAGTest(
    val id: String,
    val name: String,
    val queries: List<RAGTestQuery>,
    val evaluationMetrics: Map<String, String>? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
