package com.researchai.data.provider.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели для Claude API
 */

@Serializable
data class ClaudeApiRequest(
    val model: String,
    val messages: List<ClaudeApiMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Double = 1.0,
    @SerialName("top_p")
    val topP: Double = 1.0,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("stop_sequences")
    val stopSequences: List<String>? = null,
    val system: String? = null
)

@Serializable
data class ClaudeApiMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeApiResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeApiContent>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String?,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    val usage: ClaudeApiUsage
)

@Serializable
data class ClaudeApiContent(
    val type: String,
    val text: String
)

@Serializable
data class ClaudeApiUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

@Serializable
data class ClaudeApiError(
    val type: String,
    val error: ClaudeApiErrorDetails
)

@Serializable
data class ClaudeApiErrorDetails(
    val type: String,
    val message: String
)
