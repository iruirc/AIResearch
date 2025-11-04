package com.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeMessage>,
    val temperature: Double = 1.0
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage
)

@Serializable
data class ClaudeContent(
    val type: String,
    val text: String
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)
