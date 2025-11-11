package com.researchai.data.provider.huggingface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели для HuggingFace API
 * HuggingFace использует OpenAI-совместимый Chat Completions API
 */

@Serializable
data class HuggingFaceApiRequest(
    val model: String,
    val messages: List<HuggingFaceApiMessage>,
    val temperature: Double = 1.0,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double = 1.0,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    val stop: List<String>? = null
)

@Serializable
data class HuggingFaceApiMessage(
    val role: String,
    val content: String
)

@Serializable
data class HuggingFaceApiResponse(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String,
    val choices: List<HuggingFaceChoice>,
    val usage: HuggingFaceUsage,
    @SerialName("system_fingerprint")
    val systemFingerprint: String? = null
)

@Serializable
data class HuggingFaceChoice(
    val index: Int = 0,
    val message: HuggingFaceApiMessage,
    @SerialName("finish_reason")
    val finishReason: String?
)

@Serializable
data class HuggingFaceUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class HuggingFaceApiError(
    val error: HuggingFaceApiErrorDetails
)

@Serializable
data class HuggingFaceApiErrorDetails(
    val message: String,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)
