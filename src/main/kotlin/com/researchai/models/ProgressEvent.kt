package com.researchai.models

import kotlinx.serialization.Serializable

/**
 * Events for SSE progress streaming during RAG document creation
 */
@Serializable
data class ProgressEvent(
    val phase: String,
    val current: Int? = null,
    val total: Int? = null,
    val percent: Int? = null,
    val message: String? = null
)

@Serializable
data class CompleteEvent(
    val documentId: String,
    val name: String,
    val chunksCount: Int
)

@Serializable
data class ErrorEvent(
    val error: String
)
