package com.researchai.data.provider.ollama

import kotlinx.serialization.Serializable

/**
 * Request для Ollama Chat API
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val format: String? = null,
    val keep_alive: String? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

/**
 * Response от Ollama Chat API
 */
@Serializable
data class OllamaChatResponse(
    val model: String,
    val created_at: String,
    val message: OllamaMessage,
    val done: Boolean,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

/**
 * Response от /api/tags (список моделей)
 */
@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModelInfo>
)

@Serializable
data class OllamaModelInfo(
    val name: String,
    val modified_at: String,
    val size: Long,
    val digest: String,
    val details: OllamaModelDetails? = null
)

@Serializable
data class OllamaModelDetails(
    val format: String? = null,
    val family: String? = null,
    val parameter_size: String? = null,
    val quantization_level: String? = null
)

/**
 * Error response от Ollama API
 */
@Serializable
data class OllamaApiError(
    val error: String
)
